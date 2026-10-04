package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

public class SessionCloseTest {

    private record Close(int code, String reason) {
    }

    private final List<WebSocketSession<String, Object>> opened = new ArrayList<>();
    private final List<Close> closes = new ArrayList<>();
    private WebSocketServer<String, Object> server;

    @BeforeEach
    public void setUp() {
        server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .onOpen(opened::add)
            .onClose((_, reason, code) -> closes.add(new Close(code, reason)));
    }

    @Test
    public void shouldSendCloseFrameWhenSessionIsClosed() {
        EmbeddedChannel channel = Util.connect(server);

        CompletableFuture<Void> result = opened.getFirst().close(1000, "done").toCompletableFuture();

        // Assert close frame was sent and the connection was closed
        CloseWebSocketFrame closeFrame = assertThat(Util.readFromServer(channel)).asInstanceOf(type(CloseWebSocketFrame.class)).actual();
        assertThat(closeFrame.statusCode()).as("Should send the status code").isEqualTo(1000);
        assertThat(closeFrame.reasonText()).as("Should send the reason").isEqualTo("done");
        assertThat(channel.isOpen()).as("Channel should be closed").isFalse();
        assertThat(result.isDone()).as("Stage should be completed").isTrue();
        assertThat(result.isCompletedExceptionally()).as("Stage should complete normally").isFalse();
    }

    @Test
    public void shouldCallCloseHandlerOnceWithCodeAndReasonWhenSessionIsClosed() {
        Util.connect(server);

        opened.getFirst().close(1000, "done");

        assertThat(closes).as("Close handler should be called once with the code and reason").isEqualTo(List.of(new Close(1000, "done")));
    }

    @Test
    public void shouldNotCallCloseHandlerAgainWhenClosedSessionIsClosedAgain() {
        Util.connect(server);
        WebSocketSession<String, Object> session = opened.getFirst();

        session.close(1000, "done");
        CompletableFuture<Void> result = session.close(1001, "again").toCompletableFuture();

        // Assert second close completed without another callback
        assertThat(result.isDone()).as("Stage should be completed").isTrue();
        assertThat(result.isCompletedExceptionally()).as("Stage should complete normally").isFalse();
        assertThat(closes).as("Close handler should be called only once").isEqualTo(List.of(new Close(1000, "done")));
    }

    @Test
    public void shouldThrowWhenCloseCodeIsInvalid() {
        EmbeddedChannel channel = Util.connect(server);

        // 1005 is reserved and must not be sent in a close frame
        assertThatThrownBy(() -> opened.getFirst().close(1005, "invalid")).isInstanceOf(IllegalArgumentException.class);
        assertThat(channel.isOpen()).as("Channel should stay open").isTrue();
    }
}
