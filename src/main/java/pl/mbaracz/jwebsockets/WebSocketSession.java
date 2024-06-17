package pl.mbaracz.jwebsockets;

import io.netty.channel.ChannelHandlerContext;

import java.util.Date;
import java.util.function.BiConsumer;

/**
 * Represents a WebSocket session with a client, maintaining connection details and enabling message sending.
 *
 * @param <T> The type of messages to be sent and received.
 * @param <D> The type of additional data associated with the session.
 */
public class WebSocketSession<T, D> {

    private final BiConsumer<T, ChannelHandlerContext> messageSender;
    private final ChannelHandlerContext context;
    private final Date connectedSince;
    private Date lastMessageTime;
    private D data;

    /**
     * Constructs a new WebSocketSession.
     */
    WebSocketSession(ChannelHandlerContext context, BiConsumer<T, ChannelHandlerContext> messageSender) {
        this.context = context;
        this.connectedSince = new Date();
        this.messageSender = messageSender;
    }

    /**
     * Returns the date and time when this session was connected.
     *
     * @return The date and time of connection.
     */
    public Date getConnectedSince() {
        return connectedSince;
    }

    /**
     * Returns the date and time when the last message was received in this session.
     *
     * @return The date and time of the last message.
     */
    public Date getLastMessageTime() {
        return lastMessageTime;
    }

    /**
     * Updates the last message time to the current date and time.
     */
    void updateLastMessageTime() {
        this.lastMessageTime = new Date();
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
     * Returns the ChannelChandlerContext associated with this session.
     *
     * @return The ChannelChandlerContext.
     */
    public ChannelHandlerContext getContext() {
        return context;
    }

    /**
     * Sends a message to the client associated with this session.
     *
     * @param message The message to be sent.
     */
    public void sendMessage(T message) {
        messageSender.accept(message, context);
    }
}
