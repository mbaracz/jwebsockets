package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

public class SendMessageAsyncTest {

    private final List<WebSocketSession<String, Object>> opened = new ArrayList<>();
    private WebSocketServer<String, Object> server;

    @BeforeEach
    public void setUp() {
        server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .onOpen(opened::add);
    }

    @Test
    public void shouldCompleteSendMessageAsyncSuccessfullyWhenMessageIsWritten() {
        EmbeddedChannel channel = Util.connect(server);

        CompletableFuture<Void> result = opened.getFirst().sendMessageAsync("Hello").toCompletableFuture();

        // Assert stage completed normally and the message was written
        assertThat(result.isDone()).as("Stage should be completed").isTrue();
        assertThat(result.isCompletedExceptionally()).as("Stage should complete normally").isFalse();

        TextWebSocketFrame frame = assertThat(Util.readFromServer(channel)).asInstanceOf(type(TextWebSocketFrame.class)).actual();
        assertThat(frame.text()).as("Message should be written").isEqualTo("Hello");
        frame.release();
    }

    @Test
    public void shouldCompleteSendMessageAsyncExceptionallyWhenWriteFails() {
        EmbeddedChannel channel = Util.connect(server);

        // Close the connection before sending
        channel.close();
        CompletableFuture<Void> result = opened.getFirst().sendMessageAsync("Hello").toCompletableFuture();

        // Assert stage failed with the cause of the failed write
        assertThat(result.isCompletedExceptionally()).as("Stage should complete exceptionally").isTrue();

        assertThatThrownBy(result::join)
            .isInstanceOf(CompletionException.class)
            .as("Should fail because the channel is closed")
            .cause()
            .isInstanceOf(ClosedChannelException.class);
    }

    @Test
    public void shouldCompleteSendMessageAsyncExceptionallyWhenEncodingFails() {
        IllegalStateException encoderFailure = new IllegalStateException("Encoding failed");
        server.configure(configurer -> configurer.setMessageEncoder(_ -> {
            throw encoderFailure;
        }));

        EmbeddedChannel channel = Util.connect(server);

        // Encoder failures are reported through the stage instead of being thrown
        CompletableFuture<Void> result = opened.getFirst().sendMessageAsync("Hello").toCompletableFuture();

        // Assert stage failed with the encoder exception and nothing was written
        assertThat(result.isCompletedExceptionally()).as("Stage should complete exceptionally").isTrue();

        assertThatThrownBy(result::join)
            .isInstanceOf(CompletionException.class)
            .as("Should fail with the encoder exception")
            .cause()
            .isSameAs(encoderFailure);
        assertThat(Util.readFromServer(channel)).as("Nothing should be written").isNull();
    }
}
