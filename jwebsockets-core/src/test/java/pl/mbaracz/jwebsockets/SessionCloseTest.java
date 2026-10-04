package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

public class SessionCloseTest {

    private record Close(int code, String reason) {
    }

    private final List<WebSocketSession<String, Object>> opened = new ArrayList<>();
    private final List<String> received = new ArrayList<>();
    private final List<Close> closes = new ArrayList<>();
    private WebSocketServer<String, Object> server;

    @BeforeEach
    public void setUp() {
        server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                .setCloseTimeout(Duration.ofSeconds(5))
            )
            .onOpen(opened::add)
            .onMessage((_, message) -> received.add(message))
            .onClose((_, reason, code) -> closes.add(new Close(code, reason)));
    }

    @Test
    public void shouldSendCloseFrameAndWaitForClientWhenSessionIsClosed() {
        EmbeddedChannel channel = Util.connect(server);

        CompletableFuture<Void> result = opened.getFirst().close(1000, "done").toCompletableFuture();

        // Assert close frame was sent and the connection waits for the client's close frame
        CloseWebSocketFrame closeFrame = assertThat(Util.readFromServer(channel)).asInstanceOf(type(CloseWebSocketFrame.class)).actual();
        assertThat(closeFrame.statusCode()).as("Should send the status code").isEqualTo(1000);
        assertThat(closeFrame.reasonText()).as("Should send the reason").isEqualTo("done");
        assertThat(channel.isOpen()).as("Channel should stay open until the client answers").isTrue();
        assertThat(result.isDone()).as("Stage should not be completed yet").isFalse();
        assertThat(closes).as("Close handler should not be called yet").isEmpty();
    }

    @Test
    public void shouldCloseConnectionWithoutEchoWhenClientAnswersCloseFrame() {
        EmbeddedChannel channel = Util.connect(server);
        CompletableFuture<Void> result = opened.getFirst().close(1000, "done").toCompletableFuture();
        Util.readFromServer(channel);

        // Client answers the close frame of the server
        Util.sendFromClient(channel, new CloseWebSocketFrame(WebSocketCloseStatus.NORMAL_CLOSURE, "bye"));

        // Assert handshake completed: no second close frame, connection closed, server's status reported
        assertThat(Util.readFromServer(channel)).as("Client's close frame should not be echoed").isNull();
        assertThat(channel.isOpen()).as("Channel should be closed").isFalse();
        assertThat(result.isDone()).as("Stage should be completed").isTrue();
        assertThat(result.isCompletedExceptionally()).as("Stage should complete normally").isFalse();
        assertThat(closes).as("Close handler should be called once with the server's status").isEqualTo(List.of(new Close(1000, "done")));
    }

    @Test
    public void shouldCloseConnectionWhenClientDoesNotAnswerWithinCloseTimeout() {
        EmbeddedChannel channel = Util.connect(server);
        channel.freezeTime();

        opened.getFirst().close(1000, "done");

        // Just before the timeout
        channel.advanceTimeBy(4999, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertThat(channel.isOpen()).as("Channel should stay open before the timeout").isTrue();

        // Timeout elapsed
        channel.advanceTimeBy(1, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertThat(channel.isOpen()).as("Channel should be closed").isFalse();
        assertThat(closes).as("Close handler should be called once with the server's status").isEqualTo(List.of(new Close(1000, "done")));
    }

    @Test
    public void shouldNotSendSecondCloseFrameWhenSessionIsClosedAgain() {
        EmbeddedChannel channel = Util.connect(server);
        WebSocketSession<String, Object> session = opened.getFirst();

        session.close(1000, "done");
        Util.readFromServer(channel);
        CompletableFuture<Void> result = session.close(1001, "again").toCompletableFuture();

        // Assert second close sent nothing and completes with the first one
        assertThat(Util.readFromServer(channel)).as("Second close frame should not be sent").isNull();
        assertThat(result.isDone()).as("Stage should not be completed yet").isFalse();

        Util.sendFromClient(channel, new CloseWebSocketFrame(WebSocketCloseStatus.NORMAL_CLOSURE));

        assertThat(result.isDone()).as("Stage should be completed").isTrue();
        assertThat(closes).as("Close handler should be called only once").isEqualTo(List.of(new Close(1000, "done")));
    }

    @Test
    public void shouldCompleteCloseWhenSessionIsAlreadyClosed() {
        EmbeddedChannel channel = Util.connect(server);
        WebSocketSession<String, Object> session = opened.getFirst();
        channel.close();

        CompletableFuture<Void> result = session.close(1000, "done").toCompletableFuture();

        // Assert close of a closed connection completed without another callback
        assertThat(result.isDone()).as("Stage should be completed").isTrue();
        assertThat(result.isCompletedExceptionally()).as("Stage should complete normally").isFalse();
        assertThat(closes).as("Close handler should be called only once").hasSize(1);
    }

    @Test
    public void shouldDiscardMessagesReceivedAfterCloseFrameWasSent() {
        EmbeddedChannel channel = Util.connect(server);

        opened.getFirst().close(1000, "done");
        Util.sendFromClient(channel, new TextWebSocketFrame("late"));

        assertThat(received).as("Message sent after the close frame should not be delivered").isEmpty();
        assertThat(channel.isOpen()).as("Channel should stay open until the client answers").isTrue();
    }

    @Test
    public void shouldNotSendMessagesAfterClosingHandshakeWasStarted() {
        EmbeddedChannel channel = Util.connect(server);
        WebSocketSession<String, Object> session = opened.getFirst();

        session.close(1000, "done");
        Util.readFromServer(channel);

        CompletableFuture<Void> result = session.sendMessageAsync("late").toCompletableFuture();
        session.sendMessage("late");

        // Assert the close frame stayed the last frame sent
        assertThat(Util.readFromServer(channel)).as("Message should not be sent after the close frame").isNull();
        assertThat(result).as("Send should fail while the session is closing").isCompletedExceptionally();
    }

    @Test
    public void shouldThrowWhenCloseCodeIsInvalid() {
        EmbeddedChannel channel = Util.connect(server);

        // 1005 is reserved and must not be sent in a close frame
        assertThatThrownBy(() -> opened.getFirst().close(1005, "invalid")).isInstanceOf(IllegalArgumentException.class);
        assertThat(channel.isOpen()).as("Channel should stay open").isTrue();
    }
}
