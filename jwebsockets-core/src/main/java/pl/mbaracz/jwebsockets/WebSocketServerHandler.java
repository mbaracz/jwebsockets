package pl.mbaracz.jwebsockets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.mbaracz.jwebsockets.configuration.WebSocketServerConfiguration;
import pl.mbaracz.jwebsockets.handler.CloseHandler;
import pl.mbaracz.jwebsockets.handler.MessageHandler;
import pl.mbaracz.jwebsockets.handler.OpenHandler;
import pl.mbaracz.jwebsockets.handler.UpgradeHandler;
import pl.mbaracz.jwebsockets.handler.UpgradeResult;
import pl.mbaracz.jwebsockets.handler.WritabilityHandler;
import pl.mbaracz.jwebsockets.message.MessageDecoder;
import pl.mbaracz.jwebsockets.message.MessageEncoder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;

/**
 * Handles WebSocket and HTTP communication for the WebSocket server.
 *
 * @param <T> the type of WebSocket messages.
 * @param <D> the type of additional data associated with WebSocket sessions.
 */
public class WebSocketServerHandler<T, D> extends SimpleChannelInboundHandler<Object> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketServerHandler.class);

    private final BiFunction<T, ChannelHandlerContext, ChannelFuture> messageSender;
    private final WebSocketServer<T, D> webSocketServer;
    private WebSocketServerHandshaker handshaker;

    // Runs the application callbacks of this connection one at a time and in order,
    // null to run them on the event loop
    private final Executor callbackExecutor;

    // The session opened on this connection and whether its closure was already handled,
    // so a close frame and the following channelInactive() report it only once
    private WebSocketSession<T, D> openedSession;
    private boolean closeHandled;

    // Pending close of the connection after a heartbeat ping, cancelled only by a pong
    private ScheduledFuture<?> heartbeatTimeout;

    // Pending close of a connection that stays unwritable, cancelled when it becomes writable again
    private ScheduledFuture<?> unwritableTimeout;

    /**
     * Constructs a WebSocketServerHandler with the provided WebSocket server.
     *
     * @param webSocketServer the WebSocket server instance.
     */
    public WebSocketServerHandler(WebSocketServer<T, D> webSocketServer) {
        this.webSocketServer = webSocketServer;
        this.messageSender = getMessageSender(webSocketServer.getConfiguration());

        Executor executor = webSocketServer.getConfiguration().getCallbackExecutor();
        this.callbackExecutor = executor == null ? null : new SerialExecutor(executor);
    }

    /**
     * Determines the message sender based on the WebSocket server configuration.
     *
     * @param configuration the WebSocket server configuration.
     * @return the message sender function, returning the future of the write.
     */
    private BiFunction<T, ChannelHandlerContext, ChannelFuture> getMessageSender(WebSocketServerConfiguration<T> configuration) {
        MessageEncoder<T> encoder = configuration.getMessageEncoder();

        if (configuration.isRespondWithBinaryFrame()) {
            return (message, context) -> {
                ByteBuf byteBuf = Unpooled.wrappedBuffer(encoder.encode(message));
                return context.writeAndFlush(new BinaryWebSocketFrame(byteBuf));
            };
        }
        return (message, context) -> {
            String stringMessage = new String(encoder.encode(message), StandardCharsets.UTF_8);
            return context.writeAndFlush(new TextWebSocketFrame(stringMessage));
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

        cancelHeartbeatTimeout();
        cancelUnwritableTimeout();

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
    public void channelWritabilityChanged(ChannelHandlerContext context) {
        WritabilityHandler<T, D> writabilityHandler = webSocketServer.getWritabilityHandler();

        if (openedSession != null && !closeHandled && writabilityHandler != null) {
            WebSocketSession<T, D> session = openedSession;
            boolean writable = context.channel().isWritable();

            runCallback(context, () -> writabilityHandler.handleWritabilityChanged(session, writable));
        }

        if (openedSession != null && !closeHandled) {
            updateUnwritableTimeout(context.channel());
        }

        context.fireChannelWritabilityChanged();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        // A compressed message that cannot be inflated fails the connection. Netty reports corrupted data
        // with the inflater's DataFormatException as the cause, and a message inflating past maxMessageSize without one.
        if (cause instanceof DecompressionException) {
            WebSocketCloseStatus status = cause.getCause() instanceof DataFormatException
                ? WebSocketCloseStatus.INVALID_PAYLOAD_DATA
                : WebSocketCloseStatus.MESSAGE_TOO_BIG;

            context.writeAndFlush(new CloseWebSocketFrame(status)).addListener(ChannelFutureListener.CLOSE);
            return;
        }

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
            runCallback(context, () -> closeHandler.handleClose(session, closeInfo.reason(), closeInfo.code()));
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

        MessageHandler<T, D> messageHandler = webSocketServer.getMessageHandler();

        if (messageHandler != null) {
            runCallback(session.getChannelContext(), () -> messageHandler.handleMessage(session, message));
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

            case PongWebSocketFrame _ ->
                // A Pong needs no response (RFC 6455, section 5.5.3), but it answers a heartbeat ping
                cancelHeartbeatTimeout();

            case TextWebSocketFrame textFrame when configuration.isAllowTextFrames() ->
                handleMessageFrame(configuration.getMessageDecoder(), textFrame, session);

            case BinaryWebSocketFrame binaryFrame when configuration.isAllowBinaryFrames() ->
                handleMessageFrame(configuration.getMessageDecoder(), binaryFrame, session);

            case TextWebSocketFrame _, BinaryWebSocketFrame _ -> {
                // The endpoint does not accept this type of data (RFC 6455, section 7.4.1)
                WebSocketCloseStatus status = WebSocketCloseStatus.INVALID_MESSAGE_TYPE;
                session.close(status.code(), status.reasonText());
            }

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

        // Netty selects the first subprotocol requested by the client that is also supported (RFC 6455, section 4.2.2)
        List<String> supportedSubprotocols = webSocketServer.getConfiguration().getSubprotocols();
        String subprotocols = supportedSubprotocols.isEmpty() ? null : String.join(",", supportedSubprotocols);

        // Without compression, frames with reserved bits set fail the connection with 1002 (RFC 6455, section 5.2).
        // With it, the decoder lets them through for the decompression, and ReservedBitsValidator rejects the rest.
        boolean allowExtensions = webSocketServer.getConfiguration().isCompressionEnabled();

        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
            getWebSocketLocation(request),
            subprotocols,
            allowExtensions,
            maxFrameSize
        );

        handshaker = wsFactory.newHandshaker(request);

        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(context.channel());
            return;
        }

        UpgradeHandler<D> upgradeHandler = webSocketServer.getUpgradeHandler();
        D sessionContext = null;

        if (upgradeHandler != null) {
            HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            UpgradeResult<D> result = upgradeHandler.handleUpgrade(request, response);

            // A missing result rejects the upgrade too, so the client still gets a response
            if (result == null || !result.isAccepted()) {
                context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                return;
            }

            sessionContext = result.getContext();
        }

        WebSocketSession<T, D> session = new WebSocketSession<>(context, messageSender, sessionContext);

        handshaker.handshake(context.channel(), request).addListener(it -> {
            if (it.isSuccess()) {
                session.setSubprotocol(handshaker.selectedSubprotocol());
                webSocketServer.addSession(context.channel().id(), session);
                openedSession = session;
                // The channel may already be unwritable, with no later writability change to start the timeout
                updateUnwritableTimeout(context.channel());
                addHeartbeat(context.pipeline());
                OpenHandler<T, D> openHandler = webSocketServer.getOpenHandler();
                if (openHandler != null) {
                    runCallback(context, () -> openHandler.handleOpen(session));
                }
            }
        });
    }

    /**
     * Pings the client whenever nothing was received for the heartbeat interval.
     *
     * @param pipeline the pipeline of the opened WebSocket connection.
     */
    private void addHeartbeat(ChannelPipeline pipeline) {
        WebSocketServerConfiguration<T> configuration = webSocketServer.getConfiguration();
        Duration interval = configuration.getHeartbeatInterval();

        if (interval == null) {
            return;
        }

        Duration timeout = configuration.getHeartbeatTimeout();

        // First in the pipeline, so every read delays the ping, also fragments of a message
        pipeline.addFirst(new IdleStateHandler(interval.toNanos(), 0, 0, TimeUnit.NANOSECONDS) {
            @Override
            protected void channelIdle(ChannelHandlerContext context, IdleStateEvent event) {
                sendHeartbeatPing(context.channel(), timeout);
            }
        });
    }

    /**
     * Pings the client and closes the connection unless a pong arrives within the timeout.
     * Other messages do not answer the ping.
     *
     * @param channel the channel of the WebSocket connection.
     * @param timeout the time to wait for the pong.
     */
    private void sendHeartbeatPing(Channel channel, Duration timeout) {
        // Written from the tail of the pipeline, so the frame passes the WebSocket encoder
        channel.writeAndFlush(new PingWebSocketFrame());

        // Keep the deadline of an earlier ping that was not answered yet
        if (heartbeatTimeout == null) {
            heartbeatTimeout = channel.eventLoop()
                .schedule(() -> closeOnHeartbeatTimeout(channel), timeout.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Cancels the pending close after a heartbeat ping, if any.
     */
    private void cancelHeartbeatTimeout() {
        if (heartbeatTimeout != null) {
            heartbeatTimeout.cancel(false);
            heartbeatTimeout = null;
        }
    }

    /**
     * Closes a connection whose heartbeat ping was not answered in time.
     *
     * @param channel the channel of the WebSocket connection.
     */
    private static void closeOnHeartbeatTimeout(Channel channel) {
        CloseInfo closeInfo = new CloseInfo(WebSocketCloseStatus.ENDPOINT_UNAVAILABLE.code(), "Heartbeat timeout");

        // The client may not read anymore, so close without waiting for the close frame to be written
        channel.attr(CloseInfo.KEY).setIfAbsent(closeInfo);
        channel.writeAndFlush(new CloseWebSocketFrame(closeInfo.code(), closeInfo.reason()));
        channel.close();
    }

    /**
     * Closes the connection once it stays unwritable for the configured time and cancels that close
     * as soon as it is writable again.
     *
     * @param channel the channel of the WebSocket connection.
     */
    private void updateUnwritableTimeout(Channel channel) {
        Duration timeout = webSocketServer.getConfiguration().getUnwritableTimeout();

        if (timeout == null) {
            return;
        }

        if (channel.isWritable()) {
            cancelUnwritableTimeout();
        } else if (unwritableTimeout == null) {
            unwritableTimeout = channel.eventLoop()
                .schedule(() -> closeUnwritable(channel), timeout.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Cancels the pending close of an unwritable connection, if any.
     */
    private void cancelUnwritableTimeout() {
        if (unwritableTimeout != null) {
            unwritableTimeout.cancel(false);
            unwritableTimeout = null;
        }
    }

    /**
     * Closes a connection that stayed unwritable for too long. Its write buffer is full,
     * so no close frame is sent and the closure is reported as abnormal.
     *
     * @param channel the channel of the WebSocket connection.
     */
    private static void closeUnwritable(Channel channel) {
        CloseInfo closeInfo = new CloseInfo(WebSocketCloseStatus.ABNORMAL_CLOSURE.code(), "Unwritable timeout");

        channel.attr(CloseInfo.KEY).setIfAbsent(closeInfo);
        channel.close();
    }

    /**
     * Runs an application callback on the callback executor after the earlier callbacks of this connection,
     * or right away on the event loop if no executor is configured.
     *
     * @param context  the channel handler context.
     * @param callback the application callback.
     */
    private void runCallback(ChannelHandlerContext context, Runnable callback) {
        if (callbackExecutor == null) {
            callback.run();
            return;
        }

        callbackExecutor.execute(() -> {
            try {
                callback.run();
            } catch (RuntimeException exception) {
                // Report the failure like one thrown on the event loop, the next callbacks still run
                context.pipeline().fireExceptionCaught(exception);
            }
        });
    }

    /**
     * Sends a forbidden response to the client.
     *
     * @param context the channel handler context.
     */
    private static void sendForbiddenResponse(ChannelHandlerContext context) {
        HttpResponseStatus status = HttpResponseStatus.FORBIDDEN;
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Sends a bad request response to the client.
     *
     * @param context the channel handler context.
     */
    private static void sendBadRequestResponse(ChannelHandlerContext context) {
        HttpResponseStatus status = HttpResponseStatus.BAD_REQUEST;
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
