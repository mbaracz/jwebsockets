package pl.mbaracz.jwebsockets;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.util.AttributeKey;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Closing handshake started by the server (RFC 6455, section 7.1.2). The server sends its close frame, waits for
 * the close frame of the client and only then closes the connection, or closes it when the client does not answer in time.
 */
final class ClosingHandshake {

    // Set once the server sent its close frame, so a second one is never sent and the client's answer is not echoed
    private static final AttributeKey<Boolean> STARTED = AttributeKey.valueOf(ClosingHandshake.class, "started");

    private ClosingHandshake() {
    }

    /**
     * Sends the close frame and closes the connection if the client does not answer it within the timeout.
     * Does nothing but release the frame if the server already started closing the connection.
     *
     * @param channel the channel of the WebSocket connection.
     * @param frame   the close frame to send.
     * @param timeout the time to wait for the close frame of the client.
     * @return the future completed when the connection is closed.
     */
    static ChannelFuture start(Channel channel, CloseWebSocketFrame frame, Duration timeout) {
        if (channel.attr(STARTED).setIfAbsent(true) != null) {
            frame.release();
            return channel.closeFuture();
        }

        // Report what the frame carries to the close handler, unless the session is already being closed
        channel.attr(CloseInfo.KEY).setIfAbsent(new CloseInfo(frame.statusCode(), frame.reasonText()));

        ScheduledFuture<?> closeTimeout = channel.eventLoop()
            .schedule(() -> channel.close(), timeout.toNanos(), TimeUnit.NANOSECONDS);
        channel.closeFuture().addListener(_ -> closeTimeout.cancel(false));

        channel.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        return channel.closeFuture();
    }

    /**
     * Checks whether the server already sent its close frame on the connection.
     *
     * @param channel the channel of the WebSocket connection.
     * @return true if the server started the closing handshake, false otherwise.
     */
    static boolean isStarted(Channel channel) {
        return Boolean.TRUE.equals(channel.attr(STARTED).get());
    }
}
