package pl.mbaracz.jwebsockets.configuration;

import io.netty.handler.ssl.SslContext;
import pl.mbaracz.jwebsockets.message.MessageDecoder;
import pl.mbaracz.jwebsockets.message.MessageEncoder;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class WebSocketServerConfiguration<T> {

    /**
     * Indicates whether text frames are allowed. If false, then the text frame will not be handled.
     */
    private boolean allowTextFrames = true;

    /**
     * If true, responses will contain binary frame instead of text frame.
     */
    private boolean respondWithBinaryFrame;

    /**
     * Indicates whether binary frames are allowed. If false, then the binary frame will not be handled.
     */
    private boolean allowBinaryFrames;

    private SslContext sslContext;

    /**
     * Indicates whether the connection should be closed on an exception.
     */
    private boolean closeOnException;

    /**
     * Indicates whether ping-pong frames are enabled.
     */
    private boolean pingPongEnabled;

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
     * Enables or disables ping-pong frames.
     *
     * @param pingPongEnabled True to enable ping-pong frames, false otherwise.
     * @return The current WebSocketServerConfiguration instance.
     */
    public WebSocketServerConfiguration<T> setPingPongEnabled(boolean pingPongEnabled) {
        this.pingPongEnabled = pingPongEnabled;
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
     * Sets the allowed origins.
     *
     * @param origin Allowed origins.
     * @return The current WebSocketServerConfiguration instance.
     */
    public WebSocketServerConfiguration<T> setAllowedOrigin(String... origin) {
        this.allowedOrigins = Arrays.asList(origin);
        return this;
    }

    /**
     * Sets the allowed origins.
     *
     * @param origins List of allowed origins.
     * @return The current WebSocketServerConfiguration instance.
     */
    public WebSocketServerConfiguration<T> setAllowedOrigins(List<String> origins) {
        this.allowedOrigins = origins;
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

    public boolean isPingPongEnabled() {
        return pingPongEnabled;
    }

    public boolean isCloseOnException() {
        return closeOnException;
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
}
