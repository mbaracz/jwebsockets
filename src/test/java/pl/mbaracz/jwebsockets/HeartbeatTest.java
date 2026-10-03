package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class HeartbeatTest {

    private record Close(int code, String reason) {
    }

    private static WebSocketServer<String, Object> createServer(Duration heartbeatInterval, List<Close> closes) {
        return new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                .setHeartbeatInterval(heartbeatInterval)
                .setHeartbeatTimeout(Duration.ofSeconds(10))
            )
            .onClose((_, reason, code) -> closes.add(new Close(code, reason)));
    }

    /**
     * Connects a client and stops the clock of its channel, so the test decides when idle timers expire.
     */
    private static EmbeddedChannel connect(WebSocketServer<String, Object> server) {
        EmbeddedChannel channel = Util.connect(server);
        channel.freezeTime();
        return channel;
    }

    private static void advanceTime(EmbeddedChannel channel, long seconds) {
        channel.advanceTimeBy(seconds, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();
    }

    @Test
    public void When_HeartbeatIsDisabled_Then_NoPingShouldBeSent() {
        EmbeddedChannel channel = connect(createServer(null, new ArrayList<>()));

        advanceTime(channel, 3600);

        assertNull(Util.readFromServer(channel), "No ping should be sent");
        assertTrue(channel.isOpen(), "Channel should stay open");
    }

    @Test
    public void When_SessionIsIdle_Then_PingShouldBeSent() {
        EmbeddedChannel channel = connect(createServer(Duration.ofSeconds(30), new ArrayList<>()));

        advanceTime(channel, 30);

        assertInstanceOf(PingWebSocketFrame.class, Util.readFromServer(channel), "Should send a ping");
        assertTrue(channel.isOpen(), "Channel should stay open");
    }

    @Test
    public void When_PongArrivesInTime_Then_ConnectionShouldStayOpen() {
        List<Close> closes = new ArrayList<>();
        EmbeddedChannel channel = connect(createServer(Duration.ofSeconds(30), closes));

        advanceTime(channel, 30);
        assertInstanceOf(PingWebSocketFrame.class, Util.readFromServer(channel), "Should send a ping");

        // Answer the ping within the timeout, then wait past the moment the session would have been closed
        advanceTime(channel, 5);
        Util.sendFromClient(channel, new PongWebSocketFrame());
        advanceTime(channel, 10);

        assertTrue(channel.isOpen(), "Channel should stay open");
        assertTrue(closes.isEmpty(), "Session should not be closed");
    }

    @Test
    public void When_PongDoesNotArrive_Then_SessionShouldBeClosedOnce() {
        List<Close> closes = new ArrayList<>();
        EmbeddedChannel channel = connect(createServer(Duration.ofSeconds(30), closes));

        advanceTime(channel, 30);
        assertInstanceOf(PingWebSocketFrame.class, Util.readFromServer(channel), "Should send a ping");

        // Let the timeout pass without a pong
        advanceTime(channel, 10);

        CloseWebSocketFrame closeFrame = assertInstanceOf(CloseWebSocketFrame.class, Util.readFromServer(channel));
        assertEquals(WebSocketCloseStatus.ENDPOINT_UNAVAILABLE.code(), closeFrame.statusCode(), "Should send going away status");
        assertFalse(channel.isOpen(), "Channel should be closed");
        assertEquals(List.of(new Close(1001, "Heartbeat timeout")), closes, "Close handler should be called once");
    }

    @Test
    public void When_DataArrivesButPongDoesNot_Then_SessionShouldStillTimeOut() {
        List<Close> closes = new ArrayList<>();
        EmbeddedChannel channel = connect(createServer(Duration.ofSeconds(30), closes));

        advanceTime(channel, 30);
        assertInstanceOf(PingWebSocketFrame.class, Util.readFromServer(channel), "Should send a ping");

        // Send a message instead of the pong, it must not count as an answer to the ping
        advanceTime(channel, 5);
        Util.sendFromClient(channel, new TextWebSocketFrame("Hello"));
        advanceTime(channel, 5);

        assertFalse(channel.isOpen(), "Channel should be closed");
        assertEquals(List.of(new Close(1001, "Heartbeat timeout")), closes, "Close handler should be called once");
    }
}
