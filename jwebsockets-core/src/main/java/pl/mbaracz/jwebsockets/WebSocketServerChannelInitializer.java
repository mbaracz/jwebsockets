package pl.mbaracz.jwebsockets;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.Utf8FrameValidator;
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;
import pl.mbaracz.jwebsockets.configuration.WebSocketServerConfiguration;

/**
 * Channel initializer for setting up the pipeline for WebSocket server channels.
 *
 * @param <T> the type of WebSocket messages.
 * @param <D> the type of additional data associated with WebSocket sessions.
 */
class WebSocketServerChannelInitializer<T, D> extends ChannelInitializer<Channel> {

    private static final int COMPRESSION_LEVEL = 6;

    private final WebSocketServer<T, D> webSocketServer;

    /**
     * Constructs a WebSocketServerChannelInitializer with the provided WebSocket server.
     *
     * @param webSocketServer the WebSocket server instance.
     */
    WebSocketServerChannelInitializer(WebSocketServer<T, D> webSocketServer) {
        this.webSocketServer = webSocketServer;
    }

    /**
     * Initializes the channel pipeline with the necessary handlers for WebSocket communication.
     *
     * @param channel the socket channel being initialized.
     */
    @Override
    protected void initChannel(Channel channel) {
        ChannelPipeline pipeline = channel.pipeline();

        WebSocketServerConfiguration<T> configuration = webSocketServer.getConfiguration();

        WriteBufferWaterMark writeBufferWaterMark = configuration.getWriteBufferWaterMark();
        if (writeBufferWaterMark != null) {
            channel.config().setWriteBufferWaterMark(writeBufferWaterMark);
        }

        SslContext sslContext = configuration.getSslContext();
        if (sslContext != null) {
            pipeline.addLast(sslContext.newHandler(channel.alloc()));
        }

        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new ChunkedWriteHandler());
        pipeline.addLast(new HttpObjectAggregator(65536));

        if (configuration.isCompressionEnabled()) {
            // Negotiates permessage-deflate during the handshake, then puts the compression encoder and decoder
            // in its place, so the handlers below receive decompressed frames
            pipeline.addLast(new WebSocketServerExtensionHandler(newDeflateHandshaker(configuration.getMaxMessageSize())));

            // The frame decoder lets reserved bits through once extensions are allowed, even if none was negotiated
            pipeline.addLast(new ReservedBitsValidator());
        }

        // Fails the connection with 1007 when a text message is not valid UTF-8 (RFC 6455, section 8.1).
        // It tracks the validation state across fragments, so it must see them before they are assembled.
        pipeline.addLast(new Utf8FrameValidator());

        // Assembles fragmented messages, so the handler only receives complete frames
        pipeline.addLast(new WebSocketFrameAggregator(configuration.getMaxMessageSize()) {
            @Override
            protected void handleOversizedMessage(ChannelHandlerContext context, WebSocketFrame oversized) {
                // Close with 1009 (message too big), like the frame decoder does for an oversized frame
                context.writeAndFlush(new CloseWebSocketFrame(WebSocketCloseStatus.MESSAGE_TOO_BIG))
                    .addListener(ChannelFutureListener.CLOSE);
            }
        });

        pipeline.addLast(new WebSocketServerHandler<>(webSocketServer));
    }

    /**
     * Creates the permessage-deflate handshaker with the default compression level. It accepts server_no_context_takeover,
     * but declines server_max_window_bits, because the JDK's zlib compresses only with the full 15-bit window.
     *
     * @param maxMessageSize the maximum size of a decompressed frame.
     * @return the permessage-deflate handshaker.
     */
    private static PerMessageDeflateServerExtensionHandshaker newDeflateHandshaker(int maxMessageSize) {
        return new PerMessageDeflateServerExtensionHandshaker(
            COMPRESSION_LEVEL,
            false,
            PerMessageDeflateServerExtensionHandshaker.MAX_WINDOW_SIZE,
            true,
            false,
            maxMessageSize
        );
    }
}
