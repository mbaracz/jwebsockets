package pl.mbaracz.jwebsockets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class MessageTest {

    @Test
    public void When_MessageIsSentFromClient_Then_ExpectItInMessageHandler() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
                .configure(configurer -> configurer
                        .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                        .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                )
                .onMessage((session, message) -> {
                    assertEquals("hello", message);
                    latch.countDown();
                });

        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.performHandshake(channel, "/");

        // Construct text frame and send
        String messageToSend = "hello";
        TextWebSocketFrame textFrame = new TextWebSocketFrame(messageToSend);
        channel.writeInbound(textFrame);

        // Await to execute message handler by server
        assertTrue(latch.await(1, TimeUnit.SECONDS), "Did not receive expected message from server");
    }

    @Test
    public void When_MessageIsSentFromServer_Then_ExpectTextFrame() {
        // Construct server and listen
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
                .configure(configurer -> configurer
                        .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                        .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                );

        server.listen(8082);

        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.performHandshake(channel, "/");

        // Send message to all connected clients
        server.broadcast("hello");

        // Read outgoing frame
        TextWebSocketFrame outbound = channel.readOutbound();

        // Assert received frame content is equal to sent
        assertEquals(outbound.text(), "hello");
    }

    @Test
    public void When_UserSendBinaryFrame_And_OptionIsNotEnabled_Then_ExpectFrameIsIgnored() {
        // Construct server and listen
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
                .configure(configurer -> configurer
                        .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                        .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                        .setCloseOnException(true)
                )
                .onMessage(WebSocketSession::sendMessage);

        server.listen(8086);

        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.performHandshake(channel, "/");

        // Construct binary frame and send
        ByteBuf byteBuf = Unpooled.wrappedBuffer("hello".getBytes(StandardCharsets.UTF_8));
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(byteBuf);
        channel.writeInbound(frame);

        // Assert outbound is null
        assertNull(channel.readOutbound(), "Outbound should be null");

        // Assert channel is closed after exception is thrown
        assertFalse(channel.isOpen(), "Channel should be closed");
    }

    @Test
    public void When_UserSendBinaryFrame_And_OptionIsEnabled_Then_ExpectFrameIsHandled() {
        // Construct server and listen
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
                .configure(configurer -> configurer
                        .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                        .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                        .setAllowBinaryFrames(true)
                        .setRespondWithBinaryFrame(true)
                )
                .onMessage(WebSocketSession::sendMessage);

        server.listen(8080);

        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.performHandshake(channel, "/");

        // Construct binary frame and send
        String message = "hello";
        ByteBuf byteBuf = Unpooled.wrappedBuffer(message.getBytes(StandardCharsets.UTF_8));
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(byteBuf);
        channel.writeInbound(frame);

        // Read outgoing message
        BinaryWebSocketFrame binaryWebSocketFrame = channel.readOutbound();
        String outputMessage = binaryWebSocketFrame.content().retain().toString(StandardCharsets.UTF_8);

        // Assert outgoing message is equal to sent
        assertEquals(outputMessage, message);

        server.stop();
    }

    @Test
    public void When_UserSendTextFrame_AndOptionIsEnabled_Then_ExpectResponseFrameIsBinary() {
        // Construct server and listen
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
                .configure(configurer -> configurer
                        .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                        .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                        .setAllowBinaryFrames(true)
                        .setRespondWithBinaryFrame(true)
                )
                .onMessage(WebSocketSession::sendMessage);

        server.listen(8081);

        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.performHandshake(channel, "/");

        // Construct text frame and send
        String message = "hello";
        TextWebSocketFrame textFrame = new TextWebSocketFrame(message);
        channel.writeInbound(textFrame);

        // Read outgoing message
        BinaryWebSocketFrame binaryWebSocketFrame = channel.readOutbound();
        String outputMessage = binaryWebSocketFrame.content().retain().toString(StandardCharsets.UTF_8);

        // Assert outgoing message is equal to sent
        assertEquals(outputMessage, message);
    }
}
