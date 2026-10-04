package pl.mbaracz.jwebsockets;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.handler.UpgradeResult;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class SessionRegistrationTest {

    private static WebSocketServer<String, Object> createServer() {
        return new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            );
    }

    @Test
    public void When_ConnectionIsOpened_Then_SessionShouldNotBeRegisteredBeforeHandshake() {
        WebSocketServer<String, Object> server = createServer();

        // Open connection without sending upgrade request
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));

        // Assert no session was registered
        assertNull(server.getSessionByChannelId(channel.id()), "Session should not be registered");
        assertTrue(server.getConnectedSessions().isEmpty(), "Connected sessions should be empty");
    }

    /**
     * Creates a channel that records whether a session was registered when the HTTP response was written,
     * because rejected connections are closed (and unregistered) right after the response.
     */
    private static EmbeddedChannel createChannel(WebSocketServer<String, Object> server, AtomicBoolean registeredOnResponse) {
        return new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                registeredOnResponse.set(server.getSessionByChannelId(context.channel().id()) != null);
                context.write(message, promise);
            }
        }, new WebSocketServerHandler<>(server));
    }

    @Test
    public void When_PlainHttpRequestIsSent_Then_SessionShouldNotBeRegistered() {
        AtomicBoolean registeredOnResponse = new AtomicBoolean();
        WebSocketServer<String, Object> server = createServer();
        EmbeddedChannel channel = createChannel(server, registeredOnResponse);

        // Send http request without upgrade headers
        channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        // Assert request was rejected and no session was registered
        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status(), "Should receive bad request response");
        assertFalse(registeredOnResponse.get(), "Session should not be registered while request is rejected");
        assertNull(server.getSessionByChannelId(channel.id()), "Session should not be registered");
    }

    @Test
    public void When_UpgradeIsRejected_Then_SessionShouldNotBeRegistered() {
        AtomicBoolean registeredOnResponse = new AtomicBoolean();
        WebSocketServer<String, Object> server = createServer()
            .onUpgrade((_, _) -> UpgradeResult.reject());
        EmbeddedChannel channel = createChannel(server, registeredOnResponse);

        // Send upgrade request
        Util.performHandshake(channel, "/");

        // Assert upgrade was rejected and no session was registered
        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status(), "Should receive bad request response");
        assertFalse(registeredOnResponse.get(), "Session should not be registered while upgrade is rejected");
        assertNull(server.getSessionByChannelId(channel.id()), "Session should not be registered");
    }

    @Test
    public void When_HandshakeCannotComplete_Then_SessionShouldNotBeRegistered() {
        WebSocketServer<String, Object> server = createServer();

        // Without an HTTP codec in the pipeline the handshake fails
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.performHandshake(channel, "/");

        // Assert no session was registered
        assertNull(server.getSessionByChannelId(channel.id()), "Session should not be registered");
    }

    @Test
    public void When_HandshakeIsCompleted_Then_SessionShouldBeRegisteredBeforeOpenHandler() {
        AtomicBoolean registeredOnOpen = new AtomicBoolean();

        WebSocketServer<String, Object> server = createServer();
        server.onOpen(session -> registeredOnOpen.set(server.getConnectedSessions().contains(session)));

        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.completeHandshake(channel, "/");

        // Assert session was registered before open handler was called
        assertNotNull(server.getSessionByChannelId(channel.id()), "Session should be registered");
        assertTrue(registeredOnOpen.get(), "Session should be registered when open handler is called");
    }
}
