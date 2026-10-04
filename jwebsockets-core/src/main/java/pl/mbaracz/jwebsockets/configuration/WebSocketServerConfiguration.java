package pl.mbaracz.jwebsockets.configuration;

import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.ssl.SslContext;
import pl.mbaracz.jwebsockets.message.MessageDecoder;
import pl.mbaracz.jwebsockets.message.MessageEncoder;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

/**
 * Immutable configuration of a WebSocket server, created with its {@link Builder}.
 *
 * @param <T> the type of WebSocket messages.
 */
public final class WebSocketServerConfiguration<T> {

    /**
     * Indicates whether text frames are allowed.
     * If false, a text message closes the connection with status 1003.
     */
    private final boolean allowTextFrames;

    /**
     * If true, responses will contain binary frame instead of text frame.
     */
    private final boolean respondWithBinaryFrame;

    /**
     * Indicates whether binary frames are allowed.
     * If false, a binary message closes the connection with status 1003.
     */
    private final boolean allowBinaryFrames;

    private final SslContext sslContext;

    /**
     * Indicates whether the connection should be closed on an exception.
     */
    private final boolean closeOnException;

    /**
     * Maximum size of a message in bytes, for single frames and for messages assembled from fragments.
     */
    private final int maxMessageSize;

    /**
     * Time without received data after which a session is pinged, null if heartbeat is disabled.
     */
    private final Duration heartbeatInterval;

    /**
     * Time to wait for the pong after a heartbeat ping before the session is closed.
     */
    private final Duration heartbeatTimeout;

    /**
     * Executor running the application callbacks, null to run them on the event loop.
     */
    private final Executor callbackExecutor;

    /**
     * Write buffer watermarks of the connections, null for the Netty defaults.
     */
    private final WriteBufferWaterMark writeBufferWaterMark;

    /**
     * Time a connection may stay unwritable before it is closed, null to keep unwritable connections open.
     */
    private final Duration unwritableTimeout;

    /**
     * List of allowed origins.
     */
    private final List<String> allowedOrigins;

    /**
     * Pattern for allowed origins.
     */
    private final Pattern allowedOriginPattern;

    /**
     * Subprotocols the server supports, empty if subprotocols are not negotiated.
     */
    private final List<String> subprotocols;

    /**
     * Indicates whether the permessage-deflate compression is negotiated with clients that offer it.
     */
    private final boolean compressionEnabled;

    /**
     * Message encoder for encoding messages of type T.
     */
    private final MessageEncoder<T> messageEncoder;

    /**
     * Message decoder for decoding messages of type T.
     */
    private final MessageDecoder<T> messageDecoder;

    private WebSocketServerConfiguration(Builder<T> builder) {
        this.allowTextFrames = builder.allowTextFrames;
        this.respondWithBinaryFrame = builder.respondWithBinaryFrame;
        this.allowBinaryFrames = builder.allowBinaryFrames;
        this.sslContext = builder.sslContext;
        this.closeOnException = builder.closeOnException;
        this.maxMessageSize = builder.maxMessageSize;
        this.heartbeatInterval = builder.heartbeatInterval;
        this.heartbeatTimeout = builder.heartbeatTimeout;
        this.callbackExecutor = builder.callbackExecutor;
        this.writeBufferWaterMark = builder.writeBufferWaterMark;
        this.unwritableTimeout = builder.unwritableTimeout;
        this.allowedOrigins = builder.allowedOrigins;
        this.allowedOriginPattern = builder.allowedOriginPattern;
        this.subprotocols = builder.subprotocols;
        this.compressionEnabled = builder.compressionEnabled;
        this.messageEncoder = builder.messageEncoder;
        this.messageDecoder = builder.messageDecoder;
    }

    /**
     * Creates a builder with the default settings.
     *
     * @param <T> the type of WebSocket messages.
     * @return The new builder.
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Creates a builder starting from the settings of this configuration.
     *
     * @return The new builder.
     */
    public Builder<T> toBuilder() {
        Builder<T> builder = new Builder<>();
        builder.allowTextFrames = allowTextFrames;
        builder.respondWithBinaryFrame = respondWithBinaryFrame;
        builder.allowBinaryFrames = allowBinaryFrames;
        builder.sslContext = sslContext;
        builder.closeOnException = closeOnException;
        builder.maxMessageSize = maxMessageSize;
        builder.heartbeatInterval = heartbeatInterval;
        builder.heartbeatTimeout = heartbeatTimeout;
        builder.callbackExecutor = callbackExecutor;
        builder.writeBufferWaterMark = writeBufferWaterMark;
        builder.unwritableTimeout = unwritableTimeout;
        builder.allowedOrigins = allowedOrigins;
        builder.allowedOriginPattern = allowedOriginPattern;
        builder.subprotocols = subprotocols;
        builder.compressionEnabled = compressionEnabled;
        builder.messageEncoder = messageEncoder;
        builder.messageDecoder = messageDecoder;
        return builder;
    }

    public boolean isAllowTextFrames() {
        return allowTextFrames;
    }

    public boolean isRespondWithBinaryFrame() {
        return respondWithBinaryFrame;
    }

    public boolean isAllowBinaryFrames() {
        return allowBinaryFrames;
    }

    public SslContext getSslContext() {
        return sslContext;
    }

    public boolean isCloseOnException() {
        return closeOnException;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public Duration getHeartbeatTimeout() {
        return heartbeatTimeout;
    }

    public Executor getCallbackExecutor() {
        return callbackExecutor;
    }

    public WriteBufferWaterMark getWriteBufferWaterMark() {
        return writeBufferWaterMark;
    }

    public Duration getUnwritableTimeout() {
        return unwritableTimeout;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public Pattern getAllowedOriginPattern() {
        return allowedOriginPattern;
    }

    public List<String> getSubprotocols() {
        return subprotocols;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public MessageEncoder<T> getMessageEncoder() {
        return messageEncoder;
    }

    public MessageDecoder<T> getMessageDecoder() {
        return messageDecoder;
    }

    /**
     * Builder of a {@link WebSocketServerConfiguration}, given to the configurer of {@code WebSocketServer.configure()}.
     *
     * @param <T> the type of WebSocket messages.
     */
    public static final class Builder<T> {

        private boolean allowTextFrames = true;
        private boolean respondWithBinaryFrame;
        private boolean allowBinaryFrames;
        private SslContext sslContext;
        private boolean closeOnException;
        private int maxMessageSize = 1024 * 1024;
        private Duration heartbeatInterval;
        private Duration heartbeatTimeout = Duration.ofSeconds(10);
        private Executor callbackExecutor;
        private WriteBufferWaterMark writeBufferWaterMark;
        private Duration unwritableTimeout;
        private List<String> allowedOrigins;
        private Pattern allowedOriginPattern;
        private List<String> subprotocols = List.of();
        private boolean compressionEnabled;
        private MessageEncoder<T> messageEncoder;
        private MessageDecoder<T> messageDecoder;

        private Builder() {
        }

        public Builder<T> setSslContext(SslContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /**
         * Sets whether the connection should be closed on an exception.
         *
         * @param closeOnException True to close the connection on an exception, false otherwise.
         * @return This builder.
         */
        public Builder<T> setCloseOnException(boolean closeOnException) {
            this.closeOnException = closeOnException;
            return this;
        }

        /**
         * Sets the maximum size of a message in bytes, including all of its fragments.
         * A larger message closes the connection with status 1009 (message too big).
         *
         * @param maxMessageSize Maximum message size in bytes, must be positive.
         * @return This builder.
         */
        public Builder<T> setMaxMessageSize(int maxMessageSize) {
            if (maxMessageSize <= 0) {
                throw new IllegalArgumentException("Maximum message size must be positive!");
            }
            this.maxMessageSize = maxMessageSize;
            return this;
        }

        /**
         * Sets the heartbeat interval. A session that receives nothing for this long is sent a ping.
         *
         * @param heartbeatInterval Heartbeat interval, positive, or null to disable heartbeat (default).
         * @return This builder.
         */
        public Builder<T> setHeartbeatInterval(Duration heartbeatInterval) {
            if (heartbeatInterval != null && !heartbeatInterval.isPositive()) {
                throw new IllegalArgumentException("Heartbeat interval must be positive!");
            }
            this.heartbeatInterval = heartbeatInterval;
            return this;
        }

        /**
         * Sets how long to wait for the pong after a heartbeat ping before the session is closed.
         *
         * @param heartbeatTimeout Heartbeat timeout, positive, 10 seconds by default.
         * @return This builder.
         */
        public Builder<T> setHeartbeatTimeout(Duration heartbeatTimeout) {
            if (heartbeatTimeout == null || !heartbeatTimeout.isPositive()) {
                throw new IllegalArgumentException("Heartbeat timeout must be positive!");
            }
            this.heartbeatTimeout = heartbeatTimeout;
            return this;
        }

        /**
         * Sets the executor for the onOpen, onMessage, onWritabilityChanged and onClose callbacks, so they can block
         * without stopping the event loop. The callbacks of one session still run one at a time and in order.
         * The server never shuts the executor down, its lifecycle belongs to the application.
         *
         * @param callbackExecutor Callback executor, or null to run the callbacks on the event loop (default).
         * @return This builder.
         */
        public Builder<T> setCallbackExecutor(Executor callbackExecutor) {
            this.callbackExecutor = callbackExecutor;
            return this;
        }

        /**
         * Sets the write buffer watermarks of the connections. A connection becomes unwritable when more than the high
         * watermark waits to be written, and writable again once less than the low watermark is left.
         *
         * @param low  Low watermark in bytes.
         * @param high High watermark in bytes, at least the low watermark.
         * @return This builder.
         */
        public Builder<T> setWriteBufferWaterMark(int low, int high) {
            this.writeBufferWaterMark = new WriteBufferWaterMark(low, high);
            return this;
        }

        /**
         * Sets how long a connection may stay unwritable, for example because the client stopped reading, before it is closed.
         *
         * @param unwritableTimeout Unwritable timeout, positive, or null to keep unwritable connections open (default).
         * @return This builder.
         */
        public Builder<T> setUnwritableTimeout(Duration unwritableTimeout) {
            if (unwritableTimeout != null && !unwritableTimeout.isPositive()) {
                throw new IllegalArgumentException("Unwritable timeout must be positive!");
            }
            this.unwritableTimeout = unwritableTimeout;
            return this;
        }

        /**
         * Sets the allowed origins.
         *
         * @param origin Allowed origins.
         * @return This builder.
         */
        public Builder<T> setAllowedOrigin(String... origin) {
            this.allowedOrigins = List.of(origin);
            return this;
        }

        /**
         * Sets the allowed origins.
         *
         * @param origins List of allowed origins.
         * @return This builder.
         */
        public Builder<T> setAllowedOrigins(List<String> origins) {
            this.allowedOrigins = origins == null ? null : List.copyOf(origins);
            return this;
        }

        /**
         * Sets the supported subprotocols.
         * The handshake selects the first subprotocol requested by the client that is also supported.
         *
         * @param subprotocols Supported subprotocols.
         * @return This builder.
         */
        public Builder<T> setSubprotocols(String... subprotocols) {
            this.subprotocols = List.of(subprotocols);
            return this;
        }

        /**
         * Sets whether to negotiate the permessage-deflate compression (RFC 7692) with clients that offer it.
         *
         * @param compressionEnabled True to compress messages of clients that support it, false otherwise (default).
         * @return This builder.
         */
        public Builder<T> setCompressionEnabled(boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
            return this;
        }

        /**
         * Sets the allowed origin pattern.
         *
         * @param pattern Pattern for allowed origins.
         * @return This builder.
         */
        public Builder<T> setAllowedOrigin(Pattern pattern) {
            this.allowedOriginPattern = pattern;
            return this;
        }

        /**
         * Sets the message encoder.
         *
         * @param encoder The message encoder.
         * @return This builder.
         */
        public Builder<T> setMessageEncoder(MessageEncoder<T> encoder) {
            this.messageEncoder = encoder;
            return this;
        }

        /**
         * Sets the message decoder.
         *
         * @param decoder The message decoder.
         * @return This builder.
         */
        public Builder<T> setMessageDecoder(MessageDecoder<T> decoder) {
            this.messageDecoder = decoder;
            return this;
        }

        /**
         * Sets whether text frames are allowed.
         *
         * @param allowTextFrames True to allow text frames, false otherwise.
         * @return This builder.
         */
        public Builder<T> setAllowTextFrames(boolean allowTextFrames) {
            this.allowTextFrames = allowTextFrames;
            return this;
        }

        /**
         * Sets whether binary frames are allowed.
         *
         * @param allowBinaryFrames True to allow binary frames, false otherwise.
         * @return This builder.
         */
        public Builder<T> setAllowBinaryFrames(boolean allowBinaryFrames) {
            this.allowBinaryFrames = allowBinaryFrames;
            return this;
        }

        /**
         * Sets whether to respond with binary frames.
         *
         * @param respondWithBinaryFrame True to respond with binary frames, false otherwise.
         * @return This builder.
         */
        public Builder<T> setRespondWithBinaryFrame(boolean respondWithBinaryFrame) {
            this.respondWithBinaryFrame = respondWithBinaryFrame;
            return this;
        }

        /**
         * Builds the immutable configuration.
         *
         * @return The new WebSocketServerConfiguration instance.
         */
        public WebSocketServerConfiguration<T> build() {
            return new WebSocketServerConfiguration<>(this);
        }
    }
}
