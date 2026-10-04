package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class CloseHandlerTest {

    @Test
    public void When_CloseFrameIsSent_Then_ExpectSameCodeAndReasonOnServerSide() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        WebSocketCloseStatus status = WebSocketCloseStatus.NORMAL_CLOSURE;
        String reasonText = "foo";

        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .onClose((_, reason, code) -> {
                assertEquals(reasonText, reason, "Reason should be the same");
                assertEquals(status.code(), code, "Status code should be the same");
                latch.countDown();
            });

        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.completeHandshake(channel, "/");

        // Construct close frame and send
        CloseWebSocketFrame closeWebSocketFrame = new CloseWebSocketFrame(status, reasonText);
        channel.writeInbound(closeWebSocketFrame);

        // Read outgoing close frame
        CloseWebSocketFrame outgoingFrame = channel.readOutbound();

        // Assert expected behaviour
        assertNotNull(outgoingFrame, "Outbound frame should not be null");
        assertEquals(reasonText, outgoingFrame.reasonText(), "Reason should be the same");
        assertEquals(outgoingFrame.statusCode(), status.code(), "Status code should be the same");
        assertTrue(latch.await(1, TimeUnit.SECONDS), "Did not receive expected message from server");
    }

    private record Close(int code, String reason) {
    }

    private static WebSocketServer<String, Object> createServer(List<Close> closes) {
        return new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .onClose((_, reason, code) -> closes.add(new Close(code, reason)));
    }

    @Test
    public void When_CloseFrameIsSent_Then_CloseHandlerShouldBeCalledOnce() {
        List<Close> closes = new ArrayList<>();

        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(createServer(closes)));
        Util.completeHandshake(channel, "/");

        // Send close frame, the server answers it and closes the connection
        channel.writeInbound(new CloseWebSocketFrame(WebSocketCloseStatus.NORMAL_CLOSURE, "foo"));

        // Assert the close frame was reported once, not again when the connection became inactive
        assertFalse(channel.isOpen(), "Channel should be closed");
        assertEquals(List.of(new Close(1000, "foo")), closes, "Close handler should be called once");
    }

    @Test
    public void When_ConnectionDropsWithoutCloseFrame_Then_ExpectAbnormalClosure() {
        List<Close> closes = new ArrayList<>();
        AtomicBoolean registeredOnClose = new AtomicBoolean(true);

        WebSocketServer<String, Object> server = createServer(closes);
        server.onClose((session, reason, code) -> {
            closes.add(new Close(code, reason));
            registeredOnClose.set(server.getConnectedSessions().contains(session) || server.isSubscribed(session, "topic"));
        });

        // Construct channel, perform handshake and subscribe to a topic
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.completeHandshake(channel, "/");
        server.subscribe(server.getSessionByChannelId(channel.id()), "topic");

        // Drop the connection without a close frame
        channel.close();

        // Assert abnormal closure was reported once, after the session was cleaned up
        assertEquals(List.of(new Close(1006, "Abnormal closure")), closes, "Close handler should report abnormal closure");
        assertFalse(registeredOnClose.get(), "Session should be cleaned up before the close handler is called");
        assertFalse(server.getTopics().contains("topic"), "Empty topic should be removed");
    }

    @Test
    public void When_CloseFrameHasNoStatusCode_Then_ExpectNoStatusReceived() {
        List<Close> closes = new ArrayList<>();

        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(createServer(closes)));
        Util.completeHandshake(channel, "/");

        // Send close frame without a status code
        channel.writeInbound(new CloseWebSocketFrame());

        // Assert 1005 is reported instead of -1
        assertEquals(List.of(new Close(1005, "")), closes, "Close handler should report no status received");
    }

    @Test
    public void When_ConnectionClosesBeforeHandshake_Then_CloseHandlerShouldNotBeCalled() {
        List<Close> closes = new ArrayList<>();

        // Open and close a connection without a handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(createServer(closes)));
        channel.close();

        // Assert no session was reported as closed
        assertTrue(closes.isEmpty(), "Close handler should not be called");
    }

    @Test
    public void When_CloseFrameIsSentOverEncodedConnection_Then_ExpectSameCodeAndReason() {
        List<Close> closes = new ArrayList<>();

        // Connect through the server's pipeline, where the echoed close frame is encoded
        EmbeddedChannel channel = Util.connect(createServer(closes));

        // Send encoded close frame
        Util.sendFromClient(channel, new CloseWebSocketFrame(WebSocketCloseStatus.NORMAL_CLOSURE, "foo"));

        // Assert status was read before echoing the frame consumed it
        assertEquals(List.of(new Close(1000, "foo")), closes, "Close handler should receive the client's status");
    }

    @Test
    public void When_ServerStops_Then_CloseHandlerShouldReceiveGoingAway() {
        List<Close> closes = new ArrayList<>();
        WebSocketServer<String, Object> server = createServer(closes).listen(0);

        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.completeHandshake(channel, "/");

        // Stop the server, which closes the session with a going away close frame
        server.stop();

        // Assert the server's close status was reported once instead of an abnormal closure
        assertEquals(List.of(new Close(1001, "Endpoint unavailable")), closes, "Close handler should receive going away status");
    }
}
