package pl.mbaracz.jwebsockets;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

/**
 * Represents a WebSocket session with a client, maintaining connection details and enabling message sending.
 *
 * @param <T> The type of messages to be sent and received.
 * @param <D> The type of additional data associated with the session.
 */
public class WebSocketSession<T, D> {

    private final BiFunction<T, ChannelHandlerContext, ChannelFuture> messageSender;
    private final ChannelHandlerContext context;
    private final Instant connectedSince;
    private volatile Instant lastMessageTime;
    private volatile D data;

    /**
     * Constructs a new WebSocketSession.
     */
    WebSocketSession(ChannelHandlerContext context, BiFunction<T, ChannelHandlerContext, ChannelFuture> messageSender) {
        this.context = context;
        this.connectedSince = Instant.now();
        this.messageSender = messageSender;
    }

    /**
     * @return The instant when the connection was established.
     */
    public Instant getConnectedSince() {
        return connectedSince;
    }

    /**
     * @return The instant when the last message was received, or null if no message has been received.
     */
    public Instant getLastMessageTime() {
        return lastMessageTime;
    }

    /**
     * Updates the last message time to the current date and time.
     */
    void updateLastMessageTime() {
        this.lastMessageTime = Instant.now();
    }

    /**
     * Sets the additional data associated with this session.
     *
     * @param data The additional data to be set.
     */
    public void setData(D data) {
        this.data = data;
    }

    /**
     * Returns the additional data associated with this session.
     *
     * @return The additional data.
     */
    public D getData() {
        return data;
    }

    /**
     * Returns the ChannelHandlerContext associated with this session.
     *
     * @return The ChannelHandlerContext.
     */
    ChannelHandlerContext getContext() {
        return context;
    }

    /**
     * Checks whether the connection is currently writable according to Netty's write buffer watermarks.
     * This is a transient backpressure signal and may change immediately after this method returns.
     *
     * @return True if the connection is currently writable, false otherwise.
     */
    public boolean isWritable() {
        return context.channel().isWritable();
    }

    /**
     * Sends a message to the client associated with this session.
     * The result of the write is not reported, use {@link #sendMessageAsync(Object)}
     * to find out whether it succeeded.
     *
     * @param message The message to be sent.
     */
    public void sendMessage(T message) {
        messageSender.apply(message, context);
    }

    /**
     * Sends a message to the client associated with this session.
     *
     * @param message The message to be sent.
     * @return A stage that completes when the message is written, or exceptionally when encoding or writing fails.
     */
    public CompletionStage<Void> sendMessageAsync(T message) {
        CompletableFuture<Void> result = new CompletableFuture<>();

        try {
            messageSender.apply(message, context).addListener(write -> {
                if (write.isSuccess()) {
                    result.complete(null);
                } else {
                    result.completeExceptionally(write.cause());
                }
            });
        } catch (RuntimeException exception) {
            // Report encoder failures through the stage too, so callers handle every failure in one place
            result.completeExceptionally(exception);
        }

        return result;
    }
}
