package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.MessageEncoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

public class SendMessageAsyncTest {

    private static WebSocketServer<String, Object> createServer(MessageEncoder<String> encoder, List<WebSocketSession<String, Object>> opened) {
        return new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(encoder)
            )
            .onOpen(opened::add);
    }

    @Test
    public void When_MessageIsWritten_Then_SendMessageAsyncShouldCompleteSuccessfully() {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(PlainTextMessageEncoder.INSTANCE, opened));

        CompletableFuture<Void> result = opened.getFirst().sendMessageAsync("Hello").toCompletableFuture();

        // Assert stage completed normally and the message was written
        assertTrue(result.isDone(), "Stage should be completed");
        assertFalse(result.isCompletedExceptionally(), "Stage should complete normally");

        TextWebSocketFrame frame = assertInstanceOf(TextWebSocketFrame.class, Util.readFromServer(channel));
        assertEquals("Hello", frame.text(), "Message should be written");
    }

    @Test
    public void When_WriteFails_Then_SendMessageAsyncShouldCompleteExceptionally() {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(PlainTextMessageEncoder.INSTANCE, opened));

        // Close the connection before sending
        channel.close();
        CompletableFuture<Void> result = opened.getFirst().sendMessageAsync("Hello").toCompletableFuture();

        // Assert stage failed with the cause of the failed write
        assertTrue(result.isCompletedExceptionally(), "Stage should complete exceptionally");

        CompletionException exception = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(ClosedChannelException.class, exception.getCause(), "Should fail because the channel is closed");
    }

    @Test
    public void When_EncodingFails_Then_SendMessageAsyncShouldCompleteExceptionally() {
        IllegalStateException encoderFailure = new IllegalStateException("Encoding failed");
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(_ -> {
            throw encoderFailure;
        }, opened));

        // Encoder failures are reported through the stage instead of being thrown
        CompletableFuture<Void> result = assertDoesNotThrow(() -> opened.getFirst().sendMessageAsync("Hello").toCompletableFuture());

        // Assert stage failed with the encoder exception and nothing was written
        assertTrue(result.isCompletedExceptionally(), "Stage should complete exceptionally");

        CompletionException exception = assertThrows(CompletionException.class, result::join);
        assertSame(encoderFailure, exception.getCause(), "Should fail with the encoder exception");
        assertNull(Util.readFromServer(channel), "Nothing should be written");
    }
}
