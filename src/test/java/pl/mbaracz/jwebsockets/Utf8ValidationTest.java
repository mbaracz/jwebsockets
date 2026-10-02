package pl.mbaracz.jwebsockets;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.*;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Utf8ValidationTest {

    private static WebSocketServer<String, Object> createServer(List<String> received) {
        return new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .onMessage((_, message) -> received.add(message));
    }

    @Test
    public void When_TextFrameIsNotValidUtf8_Then_ConnectionShouldBeClosedWithInvalidPayloadData() {
        List<String> received = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(received));

        // Send a text frame ending with 0xFF, a byte that never appears in UTF-8
        Util.sendFromClient(channel, new TextWebSocketFrame(Unpooled.wrappedBuffer(new byte[]{'H', 'i', (byte) 0xFF})));

        // Assert connection was failed with an invalid payload data close frame
        CloseWebSocketFrame closeFrame = assertInstanceOf(CloseWebSocketFrame.class, Util.readFromServer(channel));
        assertEquals(WebSocketCloseStatus.INVALID_PAYLOAD_DATA.code(), closeFrame.statusCode(), "Should send invalid payload data status");
        assertFalse(channel.isOpen(), "Channel should be closed");
        assertTrue(received.isEmpty(), "Message should not be delivered");
    }

    @Test
    public void When_InvalidUtf8IsSplitAcrossFragments_Then_ConnectionShouldBeClosedWithInvalidPayloadData() {
        List<String> received = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(received));

        // End the first fragment with the lead byte of a two byte character (0xCE),
        // which the second fragment continues with an ASCII byte instead of a continuation byte
        Util.sendFromClient(channel,
            new TextWebSocketFrame(false, 0, Unpooled.wrappedBuffer(new byte[]{'H', 'i', (byte) 0xCE})),
            new ContinuationWebSocketFrame(true, 0, Unpooled.wrappedBuffer(new byte[]{'A'}))
        );

        // Assert connection was failed with an invalid payload data close frame
        CloseWebSocketFrame closeFrame = assertInstanceOf(CloseWebSocketFrame.class, Util.readFromServer(channel));
        assertEquals(WebSocketCloseStatus.INVALID_PAYLOAD_DATA.code(), closeFrame.statusCode(), "Should send invalid payload data status");
        assertFalse(channel.isOpen(), "Channel should be closed");
        assertTrue(received.isEmpty(), "Message should not be delivered");
    }

    @Test
    public void When_ValidUtf8IsSplitAcrossFragments_Then_HandlerShouldReceiveWholeMessage() {
        List<String> received = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(received));

        byte[] message = "κόσμε".getBytes(StandardCharsets.UTF_8);

        // Split the message inside its first character, which takes two bytes
        Util.sendFromClient(channel,
            new TextWebSocketFrame(false, 0, Unpooled.wrappedBuffer(message, 0, 1)),
            new ContinuationWebSocketFrame(true, 0, Unpooled.wrappedBuffer(message, 1, message.length - 1))
        );

        // Assert fragments were delivered as one message
        assertEquals(List.of("κόσμε"), received, "Should receive one assembled message");
        assertTrue(channel.isOpen(), "Channel should stay open");
    }
}
