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

public class PingPongTest {

    @Test
    public void When_ClientSendsPing_And_PingPongIsEnabled_Then_RespondWithPong() {
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
                .configure(configurer -> configurer
                        .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                        .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                        .setPingPongEnabled(true)
                );

        server.listen(8083);

        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.performHandshake(channel, "/");

        // Construct ping frame and send
        String messageToSend = "heartbeat";
        ByteBuf byteBuf = Unpooled.wrappedBuffer(messageToSend.getBytes(StandardCharsets.UTF_8));
        PingWebSocketFrame pingFrame = new PingWebSocketFrame(byteBuf);
        channel.writeInbound(pingFrame);

        // Read pong frame
        PongWebSocketFrame pongFrame = channel.readOutbound();
        String outputMessage = pongFrame.content().retain().toString(StandardCharsets.UTF_8);

        // Assert we received pong frame with the same content
        assertEquals(messageToSend, outputMessage, "Received text differs from the sent one");
    }

    @Test
    public void When_ClientSendsPing_And_PingPongIsDisabled_Then_ExpectNullOutbound() {
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
                .configure(configurer -> configurer
                        .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                        .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                );

        server.listen(8084);

        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.performHandshake(channel, "/");

        // Construct ping frame and send
        String messageToSend = "heartbeat";
        ByteBuf byteBuf = Unpooled.wrappedBuffer(messageToSend.getBytes(StandardCharsets.UTF_8));
        PingWebSocketFrame pingFrame = new PingWebSocketFrame(byteBuf);
        channel.writeInbound(pingFrame);

        // Assert we do not receive pong frame
        assertNull(channel.readOutbound(), "Outbound should be null");
    }
}
