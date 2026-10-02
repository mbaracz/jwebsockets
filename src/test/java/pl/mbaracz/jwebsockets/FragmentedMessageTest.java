package pl.mbaracz.jwebsockets;

import io.netty.buffer.ByteBuf;
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

public class FragmentedMessageTest {

    private static WebSocketServer<String, Object> createServer(int maxMessageSize, List<String> received) {
        return new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                .setAllowBinaryFrames(true)
                .setMaxMessageSize(maxMessageSize)
            )
            .onMessage((_, message) -> received.add(message));
    }

    private static ByteBuf utf8(String text) {
        return Unpooled.copiedBuffer(text, StandardCharsets.UTF_8);
    }

    @Test
    public void When_TextMessageIsFragmented_Then_HandlerShouldReceiveWholeMessage() {
        List<String> received = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(1024, received));

        // Send text message split into three fragments
        Util.sendFromClient(channel,
            new TextWebSocketFrame(false, 0, utf8("Hello, ")),
            new ContinuationWebSocketFrame(false, 0, utf8("fragmented ")),
            new ContinuationWebSocketFrame(true, 0, utf8("world"))
        );

        // Assert fragments were delivered as one message
        assertEquals(List.of("Hello, fragmented world"), received, "Should receive one assembled message");
    }

    @Test
    public void When_BinaryMessageIsFragmented_Then_HandlerShouldReceiveWholeMessage() {
        List<String> received = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(1024, received));

        // Send binary message split into two fragments
        Util.sendFromClient(channel,
            new BinaryWebSocketFrame(false, 0, utf8("binary ")),
            new ContinuationWebSocketFrame(true, 0, utf8("payload"))
        );

        // Assert fragments were delivered as one message
        assertEquals(List.of("binary payload"), received, "Should receive one assembled message");
    }

    @Test
    public void When_FragmentedMessageExceedsMaximumSize_Then_ConnectionShouldBeClosed() {
        List<String> received = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(16, received));

        // Send two fragments of 10 bytes, together exceeding the 16 byte limit
        Util.sendFromClient(channel,
            new TextWebSocketFrame(false, 0, utf8("0123456789")),
            new ContinuationWebSocketFrame(true, 0, utf8("0123456789"))
        );

        // Assert message was rejected with a message too big close frame
        CloseWebSocketFrame closeFrame = assertInstanceOf(CloseWebSocketFrame.class, Util.readFromServer(channel));
        assertEquals(WebSocketCloseStatus.MESSAGE_TOO_BIG.code(), closeFrame.statusCode(), "Should send message too big status");
        assertFalse(channel.isOpen(), "Channel should be closed");
        assertTrue(received.isEmpty(), "Message should not be delivered");
    }

    @Test
    public void When_SingleFrameExceedsMaximumSize_Then_ConnectionShouldBeClosed() {
        List<String> received = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(16, received));

        // Send a single 20 byte frame, exceeding the 16 byte limit
        Util.sendFromClient(channel, new TextWebSocketFrame(utf8("01234567890123456789")));

        // Assert message was rejected with a message too big close frame
        CloseWebSocketFrame closeFrame = assertInstanceOf(CloseWebSocketFrame.class, Util.readFromServer(channel));
        assertEquals(WebSocketCloseStatus.MESSAGE_TOO_BIG.code(), closeFrame.statusCode(), "Should send message too big status");
        assertFalse(channel.isOpen(), "Channel should be closed");
        assertTrue(received.isEmpty(), "Message should not be delivered");
    }
}
