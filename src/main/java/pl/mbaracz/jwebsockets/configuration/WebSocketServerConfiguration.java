package pl.mbaracz.jwebsockets.configuration;

import io.netty.handler.ssl.SslContext;
import pl.mbaracz.jwebsockets.message.MessageDecoder;
import pl.mbaracz.jwebsockets.message.MessageEncoder;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

public class WebSocketServerConfiguration<T> {

    /**
     * Indicates whether text frames are allowed.
     * If false, a text message closes the connection with status 1003.
     */
    private boolean allowTextFrames = true;

    /**
     * If true, responses will contain binary frame instead of text frame.
     */
    private boolean respondWithBinaryFrame;

    /**
     * Indicates whether binary frames are allowed.
     * If false, a binary message closes the connection with status 1003.
     */
    private boolean allowBinaryFrames;

    private SslContext sslContext;

    /**
     * Indicates whether the connection should be closed on an exception.
     */
    private boolean closeOnException;

    /**
     * Maximum size of a message in bytes, for single frames and for messages assembled from fragments.
     */
    private int maxMessageSize = 1024 * 1024;

    /**
     * Time without received data after which a session is pinged, null if heartbeat is disabled.
     */
    private Duration heartbeatInterval;

    /**
     * Time to wait for the pong after a heartbeat ping before the session is closed.
     */
    private Duration heartbeatTimeout = Duration.ofSeconds(10);

    /**
     * List of allowed origins.
     */
    private List<String> allowedOrigins;

    /**
     * Pattern for allowed origins.
     */
    private Pattern allowedOriginPattern;

    /**
     * Message encoder for encoding messages of type T.
     */
    private MessageEncoder<T> messageEncoder;

    /**
     * Message decoder for decoding messages of type T.
     */
    private MessageDecoder<T> messageDecoder;

    public WebSocketServerConfiguration<T> setSslContext(SslContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    /**
     * Sets whether the connection should be closed on an exception.
     *
     * @param closeOnException True to close the connection on an exception, false otherwise.
     * @return The current WebSocketServerConfiguration instance.
     */
    public WebSocketServerConfiguration<T> setCloseOnException(boolean closeOnException) {
        this.closeOnException = closeOnException;
        return this;
    }

    /**
     * Sets the maximum size of a message in bytes, including all of its fragments.
     * A larger message closes the connection with status 1009 (message too big).
     *
     * @param maxMessageSize Maximum message size in bytes, must be positive.
     * @return The current WebSocketServerConfiguration instance.
     */
    public WebSocketServerConfiguration<T> setMaxMessageSize(int maxMessageSize) {
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
     * @return The current WebSocketServerConfiguration instance.
     */
    public WebSocketServerConfiguration<T> setHeartbeatInterval(Duration heartbeatInterval) {
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
     * @return The current WebSocketServerConfiguration instance.
     */
    public WebSocketServerConfiguration<T> setHeartbeatTimeout(Duration heartbeatTimeout) {
        if (heartbeatTimeout == null || !heartbeatTimeout.isPositive()) {
            throw new IllegalArgumentException("Heartbeat timeout must be positive!");
        }
        this.heartbeatTimeout = heartbeatTimeout;
        return this;
    }

    /**
     * Sets the allowed origins.
     *
     * @param origin Allowed origins.
     * @return The current WebSocketServerConfiguration instance.
     */
    public WebSocketServerConfiguration<T> setAllowedOrigin(String... origin) {
        this.allowedOrigins = List.of(origin);
        return this;
    }

    /**
     * Sets the allowed origins.
     *
     * @param origins List of allowed origins.
     * @return The current WebSocketServerConfiguration instance.
     */
    public WebSocketServerConfiguration<T> setAllowedOrigins(List<String> origins) {
        this.allowedOrigins = origins == null ? null : List.copyOf(origins);
        return this;
    }

    /**
     * Sets the allowed origin pattern.
     *
     * @param pattern Pattern for allowed origins.
     * @return The current WebSocketServerConfiguration instance.
     */
    public WebSocketServerConfiguration<T> setAllowedOrigin(Pattern pattern) {
        this.allowedOriginPattern = pattern;
        return this;
    }

    /**
     * Sets the message encoder.
     *
     * @param encoder The message encoder.
     * @return The current WebSocketServerConfiguration instance.
     */
    public WebSocketServerConfiguration<T> setMessageEncoder(MessageEncoder<T> encoder) {
        this.messageEncoder = encoder;
        return this;
    }

    /**
     * Sets the message decoder.
     *
     * @param decoder The message decoder.
     * @return The current WebSocketServerConfiguration instance.
     */
    public WebSocketServerConfiguration<T> setMessageDecoder(MessageDecoder<T> decoder) {
        this.messageDecoder = decoder;
        return this;
    }

    /**
     * Sets whether text frames are allowed.
     *
     * @param allowTextFrames True to allow text frames, false otherwise.
     * @return The current WebSocketServerConfiguration instance.
     */
    public WebSocketServerConfiguration<T> setAllowTextFrames(boolean allowTextFrames) {
        this.allowTextFrames = allowTextFrames;
        return this;
    }

    /**
     * Sets whether binary frames are allowed.
     *
     * @param allowBinaryFrames True to allow binary frames, false otherwise.
     * @return The current WebSocketServerConfiguration instance.
     */
    public WebSocketServerConfiguration<T> setAllowBinaryFrames(boolean allowBinaryFrames) {
        this.allowBinaryFrames = allowBinaryFrames;
        return this;
    }

    /**
     * Sets whether to respond with binary frames.
     *
     * @param respondWithBinaryFrame True to respond with binary frames, false otherwise.
     * @return The current WebSocketServerConfiguration instance.
     */
    public WebSocketServerConfiguration<T> setRespondWithBinaryFrame(boolean respondWithBinaryFrame) {
        this.respondWithBinaryFrame = respondWithBinaryFrame;
        return this;
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

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public Pattern getAllowedOriginPattern() {
        return allowedOriginPattern;
    }

    public MessageEncoder<T> getMessageEncoder() {
        return messageEncoder;
    }

    public MessageDecoder<T> getMessageDecoder() {
        return messageDecoder;
    }

    /**
     * Creates a copy of this configuration, including a copy of the allowed origins list.
     *
     * @return The new WebSocketServerConfiguration instance.
     */
    public WebSocketServerConfiguration<T> copy() {
        WebSocketServerConfiguration<T> copy = new WebSocketServerConfiguration<>();
        copy.allowTextFrames = allowTextFrames;
        copy.respondWithBinaryFrame = respondWithBinaryFrame;
        copy.allowBinaryFrames = allowBinaryFrames;
        copy.sslContext = sslContext;
        copy.closeOnException = closeOnException;
        copy.maxMessageSize = maxMessageSize;
        copy.heartbeatInterval = heartbeatInterval;
        copy.heartbeatTimeout = heartbeatTimeout;
        copy.allowedOrigins = allowedOrigins == null ? null : List.copyOf(allowedOrigins);
        copy.allowedOriginPattern = allowedOriginPattern;
        copy.messageEncoder = messageEncoder;
        copy.messageDecoder = messageDecoder;
        return copy;
    }
}
