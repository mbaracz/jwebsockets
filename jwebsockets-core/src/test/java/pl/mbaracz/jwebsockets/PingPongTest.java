package pl.mbaracz.jwebsockets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class PingPongTest {

    private WebSocketServer<String, Object> server;

    @BeforeEach
    public void setUp() {
        server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            );
    }

    @AfterEach
    public void tearDown() {
        if (server.isRunning()) {
            server.stop();
        }
    }

    @Test
    public void shouldRespondWithPongWhenClientSendsPing() {
        server.listen(8083);

        // Construct channel and perform handshake
        EmbeddedChannel channel = Util.newEmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.completeHandshake(channel, "/");

        // Construct ping frame and send it without a read complete event,
        // so the pong is only received if it is flushed immediately
        String messageToSend = "heartbeat";
        ByteBuf byteBuf = Unpooled.wrappedBuffer(messageToSend.getBytes(StandardCharsets.UTF_8));
        PingWebSocketFrame pingFrame = new PingWebSocketFrame(byteBuf);
        channel.writeOneInbound(pingFrame);

        // Read pong frame
        PongWebSocketFrame pongFrame = channel.readOutbound();
        String outputMessage = pongFrame.content().toString(StandardCharsets.UTF_8);

        // Assert we received pong frame with the same content
        assertThat(outputMessage).as("Received text differs from the sent one").isEqualTo(messageToSend);
        pongFrame.release();
    }

    @Test
    public void shouldIgnorePongWhenClientSendsIt() {
        server
            .configure(configurer -> configurer.setCloseOnException(true))
            .listen(8084);

        // Construct channel and perform handshake
        EmbeddedChannel channel = Util.newEmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.completeHandshake(channel, "/");

        // Construct pong frame and send
        ByteBuf byteBuf = Unpooled.wrappedBuffer("heartbeat".getBytes(StandardCharsets.UTF_8));
        channel.writeInbound(new PongWebSocketFrame(byteBuf));

        // Assert pong is accepted without a response and without closing the connection
        assertThat(channel.<Object>readOutbound()).as("Outbound should be null").isNull();
        assertThat(channel.isOpen()).as("Channel should stay open").isTrue();
    }
}
