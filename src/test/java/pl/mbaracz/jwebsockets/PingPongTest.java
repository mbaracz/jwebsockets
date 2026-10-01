package pl.mbaracz.jwebsockets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PingPongTest {

    @Test
    public void When_ClientSendsPing_Then_RespondWithPong() {
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            );

        server.listen(8083);

        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.completeHandshake(channel, "/");

        // Construct ping frame and send it without a read complete event,
        // so the pong is only received if it is flushed immediately
        String messageToSend = "heartbeat";
        ByteBuf byteBuf = Unpooled.wrappedBuffer(messageToSend.getBytes(StandardCharsets.UTF_8));
        PingWebSocketFrame pingFrame = new PingWebSocketFrame(byteBuf);
        channel.writeOneInbound(pingFrame);

        // Read pong frame
        PongWebSocketFrame pongFrame = channel.readOutbound();
        String outputMessage = pongFrame.content().retain().toString(StandardCharsets.UTF_8);

        // Assert we received pong frame with the same content
        assertEquals(messageToSend, outputMessage, "Received text differs from the sent one");
    }

    @Test
    public void When_ClientSendsPong_Then_ShouldBeIgnored() {
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                .setCloseOnException(true)
            );

        server.listen(8084);

        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.completeHandshake(channel, "/");

        // Construct pong frame and send
        ByteBuf byteBuf = Unpooled.wrappedBuffer("heartbeat".getBytes(StandardCharsets.UTF_8));
        channel.writeInbound(new PongWebSocketFrame(byteBuf));

        // Assert pong is accepted without a response and without closing the connection
        assertNull(channel.readOutbound(), "Outbound should be null");
        assertTrue(channel.isOpen(), "Channel should stay open");
    }
}
