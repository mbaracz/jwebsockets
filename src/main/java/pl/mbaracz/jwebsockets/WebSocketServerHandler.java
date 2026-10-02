package pl.mbaracz.jwebsockets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.mbaracz.jwebsockets.configuration.WebSocketServerConfiguration;
import pl.mbaracz.jwebsockets.handler.CloseHandler;
import pl.mbaracz.jwebsockets.handler.OpenHandler;
import pl.mbaracz.jwebsockets.handler.UpgradeHandler;
import pl.mbaracz.jwebsockets.message.MessageDecoder;
import pl.mbaracz.jwebsockets.message.MessageEncoder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * Handles WebSocket and HTTP communication for the WebSocket server.
 *
 * @param <T> the type of WebSocket messages.
 * @param <D> the type of additional data associated with WebSocket sessions.
 */
public class WebSocketServerHandler<T, D> extends SimpleChannelInboundHandler<Object> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketServerHandler.class);

    private final BiConsumer<T, ChannelHandlerContext> messageSender;
    private final WebSocketServer<T, D> webSocketServer;
    private WebSocketServerHandshaker handshaker;

    // The session opened on this connection and whether its closure was already handled,
    // so a close frame and the following channelInactive() report it only once
    private WebSocketSession<T, D> openedSession;
    private boolean closeHandled;

    /**
     * Constructs a WebSocketServerHandler with the provided WebSocket server.
     *
     * @param webSocketServer the WebSocket server instance.
     */
    public WebSocketServerHandler(WebSocketServer<T, D> webSocketServer) {
        this.webSocketServer = webSocketServer;
        this.messageSender = getMessageSender(webSocketServer.getConfiguration());
    }

    /**
     * Determines the message sender based on the WebSocket server configuration.
     *
     * @param configuration the WebSocket server configuration.
     * @return the message sender function.
     */
    private BiConsumer<T, ChannelHandlerContext> getMessageSender(WebSocketServerConfiguration<T> configuration) {
        MessageEncoder<T> encoder = configuration.getMessageEncoder();

        if (configuration.isRespondWithBinaryFrame()) {
            return (message, context) -> {
                ByteBuf byteBuf = Unpooled.wrappedBuffer(encoder.encode(message));
                context.writeAndFlush(new BinaryWebSocketFrame(byteBuf));
            };
        }
        return (message, context) -> {
            String stringMessage = new String(encoder.encode(message), StandardCharsets.UTF_8);
            context.writeAndFlush(new TextWebSocketFrame(stringMessage));
        };
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Object object) {
        if (object instanceof FullHttpRequest) {
            logger.debug("Received FullHttpRequest from channel with id {}", context.channel().id());
            handleHttpRequest(context, (FullHttpRequest) object);
        } else if (object instanceof WebSocketFrame) {
            handleWebSocketFrame(context, (WebSocketFrame) object);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext context) {
        context.flush();
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) {
        ChannelId channelId = context.channel().id();
        logger.debug("Channel with id {} is now inactive", channelId);

        if (openedSession != null) {
            // Use the status the server closed the connection with, otherwise no close frame
            // was exchanged and the closure is abnormal (RFC 6455, section 7.1.5)
            CloseInfo closeInfo = context.channel().attr(CloseInfo.KEY).get();

            if (closeInfo == null) {
                closeInfo = CloseInfo.of(WebSocketCloseStatus.ABNORMAL_CLOSURE);
            }

            handleSessionClosed(context, openedSession, closeInfo);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        logger.error("Exception caught in channel with id {}", context.channel().id(), cause);
        if (webSocketServer.getConfiguration().isCloseOnException()) {
            context.close();
        }
    }

    /**
     * Constructs the WebSocket location URL based on the request.
     *
     * @param request the HTTP request.
     * @return the WebSocket location URL.
     */
    private String getWebSocketLocation(FullHttpRequest request) {
        String location = request.headers().get(HttpHeaderNames.HOST) + webSocketServer.getPath();

        String prefix = webSocketServer.getConfiguration().getSslContext() != null
            ? "wss"
            : "ws";

        return prefix + "://" + location;
    }

    /**
     * Determines if the HTTP request should be upgraded to WebSocket.
     *
     * @param request the HTTP request.
     * @return true if the request should be upgraded, false otherwise.
     */
    private boolean shouldUpgrade(FullHttpRequest request) {
        if (request.method() != HttpMethod.GET) return false;
        if (request.decoderResult().isFailure()) return false;

        // Both headers are comma separated token lists compared case-insensitively (RFC 6455, section 4.2.1)
        HttpHeaders headers = request.headers();
        if (!headers.containsValue(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true)) return false;
        if (!headers.containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true)) return false;

        return webSocketServer.getPath().equals(getRequestPath(request));
    }

    /**
     * Returns the path of the request URI without its query string.
     *
     * @param request the HTTP request.
     * @return the request path, or null if the request URI is malformed.
     */
    private String getRequestPath(FullHttpRequest request) {
        try {
            return URI.create(request.uri()).getRawPath();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Checks if the origin of the request allows WebSocket upgrade.
     *
     * @param request the HTTP request.
     * @return true if the origin is allowed, false otherwise.
     */
    private boolean isUpgradeFromAllowedOrigin(FullHttpRequest request) {
        String origin = request.headers().get(HttpHeaderNames.ORIGIN);
        WebSocketServerConfiguration<T> configuration = webSocketServer.getConfiguration();

        if (isOriginNotAllowedByPattern(origin, configuration.getAllowedOriginPattern())) {
            return false;
        }

        return isOriginAllowedInList(origin, configuration.getAllowedOrigins());
    }

    /**
     * Checks if the origin is not allowed based on the configured pattern.
     *
     * @param origin               the origin header value.
     * @param allowedOriginPattern the pattern for allowed origins.
     * @return true if the origin is not allowed, false otherwise.
     */
    private boolean isOriginNotAllowedByPattern(String origin, Pattern allowedOriginPattern) {
        return allowedOriginPattern != null && (origin == null || !allowedOriginPattern.matcher(origin).matches());
    }

    /**
     * Checks if the origin is allowed based on the configured list of allowed origins.
     *
     * @param origin         the origin header value.
     * @param allowedOrigins the list of allowed origins.
     * @return true if the origin is allowed, false otherwise.
     */
    private boolean isOriginAllowedInList(String origin, List<String> allowedOrigins) {
        return allowedOrigins == null || (origin != null && allowedOrigins.contains(origin));
    }

    /**
     * Handles close frames received over WebSocket.
     *
     * @param context    the channel handler context.
     * @param closeFrame the close frame received.
     * @param session    the WebSocket session associated with the frame.
     */
    private void handleCloseFrame(ChannelHandlerContext context, CloseWebSocketFrame closeFrame, WebSocketSession<T, D> session) {
        // Read the status before echoing the frame, because writing it consumes its content.
        // A close frame without a status code is reported as 1005 (RFC 6455, section 7.1.5).
        int code = closeFrame.statusCode() == -1 ? WebSocketCloseStatus.EMPTY.code() : closeFrame.statusCode();
        String reason = closeFrame.reasonText();

        handshaker.close(context.channel(), closeFrame.retain());
        handleSessionClosed(context, session, new CloseInfo(code, reason));
    }

    /**
     * Removes the session from the server and its topics, then notifies the close handler.
     * Runs at most once per connection.
     *
     * @param context   the channel handler context.
     * @param session   the WebSocket session that was closed.
     * @param closeInfo the close status reported to the close handler.
     */
    private void handleSessionClosed(ChannelHandlerContext context, WebSocketSession<T, D> session, CloseInfo closeInfo) {
        if (closeHandled) {
            return;
        }

        closeHandled = true;
        webSocketServer.removeSession(context.channel().id());

        CloseHandler<T, D> closeHandler = webSocketServer.getCloseHandler();

        if (closeHandler != null) {
            closeHandler.handleClose(session, closeInfo.reason(), closeInfo.code());
        }
    }

    /**
     * Handles message frames received over WebSocket.
     *
     * @param decoder the decoder for WebSocket messages.
     * @param frame   the WebSocket frame received.
     * @param session the WebSocket session associated with the frame.
     */
    private void handleMessageFrame(MessageDecoder<T> decoder, WebSocketFrame frame, WebSocketSession<T, D> session) {
        ByteBuf content = frame.content();
        byte[] bytes = new byte[content.readableBytes()];
        content.getBytes(content.readerIndex(), bytes);

        T message = decoder.decode(bytes);
        session.updateLastMessageTime();

        if (webSocketServer.getMessageHandler() != null) {
            webSocketServer.getMessageHandler().handleMessage(session, message);
        }
    }

    /**
     * Handles WebSocket frames.
     *
     * @param context the channel handler context.
     * @param frame   the WebSocket frame.
     */
    private void handleWebSocketFrame(ChannelHandlerContext context, WebSocketFrame frame) {
        WebSocketSession<T, D> session = webSocketServer.getSessionByChannelId(context.channel().id());

        if (session == null) {
            logger.warn("Received {} while session is null!", frame.getClass());
            return;
        }

        WebSocketServerConfiguration<T> configuration = webSocketServer.getConfiguration();

        switch (frame) {
            case CloseWebSocketFrame closeFrame -> handleCloseFrame(context, closeFrame, session);

            case PingWebSocketFrame pingFrame ->
                // A Ping must be answered with a Pong carrying the same payload (RFC 6455, section 5.5.2)
                context.writeAndFlush(new PongWebSocketFrame(pingFrame.content().retain()));

            case PongWebSocketFrame _ -> {
                // A Pong needs no response (RFC 6455, section 5.5.3)
            }

            case TextWebSocketFrame textFrame when configuration.isAllowTextFrames() ->
                handleMessageFrame(configuration.getMessageDecoder(), textFrame, session);

            case BinaryWebSocketFrame binaryFrame when configuration.isAllowBinaryFrames() ->
                handleMessageFrame(configuration.getMessageDecoder(), binaryFrame, session);

            case TextWebSocketFrame _, BinaryWebSocketFrame _ ->
                throw new UnsupportedOperationException("%s frame type is disabled by configuration".formatted(frame.getClass().getName()));

            default ->
                throw new UnsupportedOperationException("%s frame type is not supported".formatted(frame.getClass().getName()));
        }
    }

    /**
     * Handles HTTP requests.
     *
     * @param context the channel handler context.
     * @param request the HTTP request.
     */
    private void handleHttpRequest(ChannelHandlerContext context, FullHttpRequest request) {
        if (!shouldUpgrade(request)) {
            sendBadRequestResponse(context);
            return;
        }

        if (!isUpgradeFromAllowedOrigin(request)) {
            sendForbiddenResponse(context);
            return;
        }

        // A single frame may be as large as a whole message, larger messages are rejected with 1009 by the decoder
        int maxFrameSize = webSocketServer.getConfiguration().getMaxMessageSize();

        // No extension is negotiated, so frames with reserved bits set fail the connection with 1002 (RFC 6455, section 5.2)
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
            getWebSocketLocation(request),
            null,
            false,
            maxFrameSize
        );

        handshaker = wsFactory.newHandshaker(request);

        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(context.channel());
            return;
        }

        UpgradeHandler<T, D> upgradeHandler = webSocketServer.getUpgradeHandler();

        WebSocketSession<T, D> session = new WebSocketSession<>(context, messageSender);

        if (upgradeHandler != null) {
            HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);

            if (!upgradeHandler.handleUpgrade(request, session, response)) {
                context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                return;
            }
        }

        handshaker.handshake(context.channel(), request).addListener(it -> {
            if (it.isSuccess()) {
                webSocketServer.addSession(context.channel().id(), session);
                openedSession = session;
                OpenHandler<T, D> openHandler = webSocketServer.getOpenHandler();
                if (openHandler != null) {
                    openHandler.handleOpen(session);
                }
            }
        });
    }

    /**
     * Sends a forbidden response to the client.
     *
     * @param context the channel handler context.
     */
    private void sendForbiddenResponse(ChannelHandlerContext context) {
        HttpResponseStatus status = HttpResponseStatus.FORBIDDEN;
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Sends a bad request response to the client.
     *
     * @param context the channel handler context.
     */
    private void sendBadRequestResponse(ChannelHandlerContext context) {
        HttpResponseStatus status = HttpResponseStatus.BAD_REQUEST;
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
