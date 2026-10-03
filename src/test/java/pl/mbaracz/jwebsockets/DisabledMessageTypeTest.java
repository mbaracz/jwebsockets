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

public class DisabledMessageTypeTest {

    private static WebSocketServer<String, Object> createServer(boolean allowText, boolean allowBinary, List<String> received, List<Integer> closeCodes) {
        return new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                .setAllowTextFrames(allowText)
                .setAllowBinaryFrames(allowBinary)
            )
            .onMessage((_, message) -> received.add(message))
            .onClose((_, _, code) -> closeCodes.add(code));
    }

    @Test
    public void When_TextMessagesAreDisabled_Then_TextMessageShouldCloseConnectionWithInvalidMessageType() {
        List<String> received = new ArrayList<>();
        List<Integer> closeCodes = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(false, true, received, closeCodes));

        Util.sendFromClient(channel, new TextWebSocketFrame("Hello"));

        // Assert connection was closed with an invalid message type close frame
        CloseWebSocketFrame closeFrame = assertInstanceOf(CloseWebSocketFrame.class, Util.readFromServer(channel));
        assertEquals(WebSocketCloseStatus.INVALID_MESSAGE_TYPE.code(), closeFrame.statusCode(), "Should send invalid message type status");
        assertFalse(channel.isOpen(), "Channel should be closed");
        assertTrue(received.isEmpty(), "Message should not be delivered");
        assertEquals(List.of(WebSocketCloseStatus.INVALID_MESSAGE_TYPE.code()), closeCodes, "Close handler should get the invalid message type status");
    }

    @Test
    public void When_BinaryMessagesAreDisabled_Then_BinaryMessageShouldCloseConnectionWithInvalidMessageType() {
        List<String> received = new ArrayList<>();
        List<Integer> closeCodes = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(true, false, received, closeCodes));

        Util.sendFromClient(channel, new BinaryWebSocketFrame(Unpooled.copiedBuffer("Hello", StandardCharsets.UTF_8)));

        // Assert connection was closed with an invalid message type close frame
        CloseWebSocketFrame closeFrame = assertInstanceOf(CloseWebSocketFrame.class, Util.readFromServer(channel));
        assertEquals(WebSocketCloseStatus.INVALID_MESSAGE_TYPE.code(), closeFrame.statusCode(), "Should send invalid message type status");
        assertFalse(channel.isOpen(), "Channel should be closed");
        assertTrue(received.isEmpty(), "Message should not be delivered");
        assertEquals(List.of(WebSocketCloseStatus.INVALID_MESSAGE_TYPE.code()), closeCodes, "Close handler should get the invalid message type status");
    }
}
