package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.Util;
import pl.mbaracz.jwebsockets.WebSocketServer;
import pl.mbaracz.jwebsockets.WebSocketServerHandler;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
                .onClose((session, reason, code) -> {
                    assertEquals(reason, reasonText, "Reason should be the same");
                    assertEquals(status.code(), code, "Status code should be the same");
                    latch.countDown();
                });

        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.performHandshake(channel, "/");

        // Construct close frame and send
        CloseWebSocketFrame closeWebSocketFrame = new CloseWebSocketFrame(status, reasonText);
        channel.writeInbound(closeWebSocketFrame);

        // Read outgoing close frame
        CloseWebSocketFrame outgoingFrame = channel.readOutbound();

        // Assert expected behaviour
        assertNotNull(outgoingFrame, "Outbound frame should not be null");
        assertEquals(outgoingFrame.reasonText(), reasonText, "Reason should be the same");
        assertEquals(outgoingFrame.statusCode(), status.code(), "Status code should be the same");
        assertTrue(latch.await(1, TimeUnit.SECONDS), "Did not receive expected message from server");
    }
}
