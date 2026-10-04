package pl.mbaracz.jwebsockets;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReservedBitsTest {

    // RSV1, RSV2 and RSV3, the three reserved bits of a frame
    @ParameterizedTest
    @ValueSource(ints = {0b100, 0b010, 0b001})
    public void When_FrameHasReservedBitSet_Then_ConnectionShouldBeClosedWithProtocolError(int rsv) {
        List<String> received = new ArrayList<>();

        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .onMessage((_, message) -> received.add(message));

        EmbeddedChannel channel = Util.connect(server);

        // Send a text frame with a reserved bit set, although no extension was negotiated
        Util.sendFromClient(channel, new TextWebSocketFrame(true, rsv, Unpooled.copiedBuffer("Hello", StandardCharsets.UTF_8)));

        // Assert connection was failed with a protocol error close frame
        CloseWebSocketFrame closeFrame = assertInstanceOf(CloseWebSocketFrame.class, Util.readFromServer(channel));
        assertEquals(WebSocketCloseStatus.PROTOCOL_ERROR.code(), closeFrame.statusCode(), "Should send protocol error status");
        assertFalse(channel.isOpen(), "Channel should be closed");
        assertTrue(received.isEmpty(), "Message should not be delivered");
    }
}
