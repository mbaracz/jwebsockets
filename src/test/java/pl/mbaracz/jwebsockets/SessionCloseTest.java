package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class SessionCloseTest {

    private record Close(int code, String reason) {
    }

    private static WebSocketServer<String, Object> createServer(List<WebSocketSession<String, Object>> opened, List<Close> closes) {
        return new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .onOpen(opened::add)
            .onClose((_, reason, code) -> closes.add(new Close(code, reason)));
    }

    @Test
    public void When_SessionIsClosed_Then_CloseFrameShouldBeSent() {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(opened, new ArrayList<>()));

        CompletableFuture<Void> result = opened.getFirst().close(1000, "done").toCompletableFuture();

        // Assert close frame was sent and the connection was closed
        CloseWebSocketFrame closeFrame = assertInstanceOf(CloseWebSocketFrame.class, Util.readFromServer(channel));
        assertEquals(1000, closeFrame.statusCode(), "Should send the status code");
        assertEquals("done", closeFrame.reasonText(), "Should send the reason");
        assertFalse(channel.isOpen(), "Channel should be closed");
        assertTrue(result.isDone(), "Stage should be completed");
        assertFalse(result.isCompletedExceptionally(), "Stage should complete normally");
    }

    @Test
    public void When_SessionIsClosed_Then_CloseHandlerShouldBeCalledOnceWithCodeAndReason() {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        List<Close> closes = new ArrayList<>();
        Util.connect(createServer(opened, closes));

        opened.getFirst().close(1000, "done");

        assertEquals(List.of(new Close(1000, "done")), closes, "Close handler should be called once with the code and reason");
    }

    @Test
    public void When_ClosedSessionIsClosedAgain_Then_CloseHandlerShouldNotBeCalledAgain() {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        List<Close> closes = new ArrayList<>();
        Util.connect(createServer(opened, closes));
        WebSocketSession<String, Object> session = opened.getFirst();

        session.close(1000, "done");
        CompletableFuture<Void> result = session.close(1001, "again").toCompletableFuture();

        // Assert second close completed without another callback
        assertTrue(result.isDone(), "Stage should be completed");
        assertFalse(result.isCompletedExceptionally(), "Stage should complete normally");
        assertEquals(List.of(new Close(1000, "done")), closes, "Close handler should be called only once");
    }

    @Test
    public void When_CloseCodeIsInvalid_Then_ShouldThrow() {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(opened, new ArrayList<>()));

        // 1005 is reserved and must not be sent in a close frame
        assertThrows(IllegalArgumentException.class, () -> opened.getFirst().close(1005, "invalid"));
        assertTrue(channel.isOpen(), "Channel should stay open");
    }
}
