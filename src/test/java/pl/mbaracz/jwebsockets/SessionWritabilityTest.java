package pl.mbaracz.jwebsockets;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class SessionWritabilityTest {

    private static WebSocketServer<String, Object> createServer(List<WebSocketSession<String, Object>> opened) {
        return new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .onOpen(opened::add);
    }

    /**
     * Holds back flushes like a client that stops reading, so written messages stay in the write buffer.
     */
    private static void holdFlushes(EmbeddedChannel channel) {
        channel.pipeline().addFirst(new ChannelOutboundHandlerAdapter() {
            @Override
            public void flush(ChannelHandlerContext context) {
            }
        });
    }

    private record Close(int code, String reason) {
    }

    private static WebSocketServer<String, Object> createServer(Duration unwritableTimeout, List<Close> closes) {
        return new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                .setWriteBufferWaterMark(1024, 2048)
                .setUnwritableTimeout(unwritableTimeout)
            )
            .onClose((_, reason, code) -> closes.add(new Close(code, reason)));
    }

    /**
     * Connects a client that stops reading and writes a message above the high watermark to it,
     * with the clock of the channel stopped, so the test decides when timeouts expire.
     */
    private static EmbeddedChannel connectSlowClient(WebSocketServer<String, Object> server) {
        EmbeddedChannel channel = Util.connect(server);
        channel.freezeTime();
        holdFlushes(channel);
        channel.writeAndFlush(new TextWebSocketFrame("x".repeat(4096)));
        return channel;
    }

    private static void advanceTime(EmbeddedChannel channel, long seconds) {
        channel.advanceTimeBy(seconds, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();
    }

    @Test
    public void When_SessionIsConnected_Then_ShouldInitiallyBeWritable() {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(opened));

        assertTrue(opened.getFirst().isWritable(), "Session should be writable");

        channel.close();
    }

    @Test
    public void When_WriteBufferExceedsHighWaterMark_Then_SessionShouldNotBeWritableUntilFlushed() {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(opened));
        WebSocketSession<String, Object> session = opened.getFirst();

        channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(1024, 2048));
        holdFlushes(channel);

        session.sendMessage("x".repeat(4096));

        assertTrue(channel.isOpen(), "Channel should stay open");
        assertFalse(session.isWritable(), "Session should not be writable above the high water mark");

        // Flush the buffered message
        channel.pipeline().removeFirst();
        channel.flush();

        assertTrue(session.isWritable(), "Session should be writable again below the low water mark");

        channel.close();
    }

    @Test
    public void When_WriteBufferCrossesWaterMarks_Then_WritabilityHandlerShouldBeNotified() {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        List<Boolean> notifications = new ArrayList<>();

        WebSocketServer<String, Object> server = createServer(opened)
            .onWritabilityChanged((_, writable) -> notifications.add(writable));

        EmbeddedChannel channel = Util.connect(server);
        channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(1024, 2048));
        holdFlushes(channel);

        opened.getFirst().sendMessage("x".repeat(4096));

        assertEquals(List.of(false), notifications, "Should be notified when the high water mark is exceeded");

        // Flush the buffered message
        channel.pipeline().removeFirst();
        channel.flush();

        assertEquals(List.of(false, true), notifications, "Should be notified when the buffer drops below the low water mark");

        channel.close();
    }

    @Test
    public void When_ChannelIsClosed_Then_SessionShouldNotBeWritable() {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(opened));

        // Close the connection
        channel.close();

        assertFalse(opened.getFirst().isWritable(), "Session should not be writable");
    }

    @Test
    public void When_WriteBufferExceedsConfiguredHighWaterMark_Then_SessionShouldNotBeClosedImmediately() {
        List<Close> closes = new ArrayList<>();
        EmbeddedChannel channel = connectSlowClient(createServer(Duration.ofSeconds(10), closes));

        advanceTime(channel, 5);

        assertFalse(channel.isWritable(), "Channel should not be writable above the configured high watermark");
        assertTrue(channel.isOpen(), "Channel should stay open before the timeout");
        assertTrue(closes.isEmpty(), "Session should not be closed");
    }

    @Test
    public void When_ChannelBecomesWritableBeforeTimeout_Then_SessionShouldNotBeClosed() {
        List<Close> closes = new ArrayList<>();
        EmbeddedChannel channel = connectSlowClient(createServer(Duration.ofSeconds(10), closes));

        advanceTime(channel, 5);

        // Flush the buffered message, which brings the buffer below the low watermark
        channel.pipeline().removeFirst();
        channel.flush();
        advanceTime(channel, 10);

        assertTrue(channel.isWritable(), "Channel should be writable again");
        assertTrue(channel.isOpen(), "Channel should stay open");
        assertTrue(closes.isEmpty(), "Session should not be closed");
    }

    @Test
    public void When_ChannelStaysUnwritableUntilTimeout_Then_SessionShouldBeClosedOnce() {
        List<Close> closes = new ArrayList<>();
        EmbeddedChannel channel = connectSlowClient(createServer(Duration.ofSeconds(10), closes));

        advanceTime(channel, 10);

        assertFalse(channel.isOpen(), "Channel should be closed");
        assertEquals(List.of(new Close(1006, "Unwritable timeout")), closes, "Close handler should be called once");
    }

    @Test
    public void When_UnwritableTimeoutIsNotConfigured_Then_UnwritableSessionShouldStayOpen() {
        List<Close> closes = new ArrayList<>();
        EmbeddedChannel channel = connectSlowClient(createServer(null, closes));

        advanceTime(channel, 3600);

        assertFalse(channel.isWritable(), "Channel should stay unwritable");
        assertTrue(channel.isOpen(), "Channel should stay open");
        assertTrue(closes.isEmpty(), "Session should not be closed");
    }

    @Test
    public void When_ChannelIsAlreadyUnwritableWhenSessionOpens_Then_UnwritableTimeoutShouldStillStart() {
        List<Close> closes = new ArrayList<>();
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerChannelInitializer<>(createServer(Duration.ofSeconds(10), closes)));
        channel.freezeTime();

        // Make the channel unwritable before the handshake, so no writability change follows the opening of the session
        channel.unsafe().outboundBuffer().setUserDefinedWritability(1, false);
        channel.runPendingTasks();
        Util.performHandshake(channel, "/");

        advanceTime(channel, 10);

        assertFalse(channel.isOpen(), "Channel should be closed");
        assertEquals(List.of(new Close(1006, "Unwritable timeout")), closes, "Close handler should be called once");
    }
}
