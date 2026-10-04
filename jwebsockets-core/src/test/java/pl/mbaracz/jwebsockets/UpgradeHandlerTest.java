package pl.mbaracz.jwebsockets;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.handler.UpgradeResult;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UpgradeHandlerTest {

    private record User(String name) {
    }

    /**
     * Creates a server accepting upgrades with a cookie, which becomes the session context.
     */
    private static WebSocketServer<String, User> createServer(List<User> opened, List<User> received) {
        return new WebSocketServer<String, User>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .onUpgrade((request, response) -> {
                String cookie = request.headers().get(HttpHeaderNames.COOKIE);

                if (cookie == null) {
                    response.setStatus(HttpResponseStatus.UNAUTHORIZED);
                    return UpgradeResult.reject();
                }

                return UpgradeResult.accept(new User(cookie));
            })
            .onOpen(session -> opened.add(session.getContext()))
            .onMessage((session, _) -> received.add(session.getContext()));
    }

    /**
     * Sends an upgrade request through the server pipeline, with the cookie unless it is null.
     */
    private static EmbeddedChannel upgrade(WebSocketServer<String, User> server, String cookie) {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerChannelInitializer<>(server));
        FullHttpRequest request = Util.createHttpRequest("/");

        if (cookie != null) {
            request.headers().set(HttpHeaderNames.COOKIE, cookie);
        }

        channel.writeInbound(request);
        return channel;
    }

    /**
     * Decodes the response written by the server the way a client does.
     */
    private static HttpResponse readResponse(EmbeddedChannel channel) {
        EmbeddedChannel client = new EmbeddedChannel(new HttpResponseDecoder());
        ByteBuf buffer;

        while ((buffer = channel.readOutbound()) != null) {
            client.writeInbound(buffer);
        }

        return client.readInbound();
    }

    @Test
    public void When_UpgradeIsRejected_Then_NoSessionShouldBeOpened() {
        List<User> opened = new ArrayList<>();
        WebSocketServer<String, User> server = createServer(opened, new ArrayList<>());

        EmbeddedChannel channel = upgrade(server, null);

        assertFalse(channel.isOpen(), "Channel should be closed");
        assertTrue(opened.isEmpty(), "No session should be opened");
        assertTrue(server.getConnectedSessions().isEmpty(), "No session should be registered");
    }

    @Test
    public void When_UpgradeIsRejected_Then_ResponseStatusSetByHandlerShouldBeSent() {
        EmbeddedChannel channel = upgrade(createServer(new ArrayList<>(), new ArrayList<>()), null);

        HttpResponse response = readResponse(channel);

        assertEquals(HttpResponseStatus.UNAUTHORIZED, response.status(), "Should send the status set by the upgrade handler");
    }

    @Test
    public void When_UpgradeIsAccepted_Then_ContextShouldBeAvailableOnOpen() {
        List<User> opened = new ArrayList<>();
        EmbeddedChannel channel = upgrade(createServer(opened, new ArrayList<>()), "alice");

        HttpResponse response = readResponse(channel);

        assertEquals(HttpResponseStatus.SWITCHING_PROTOCOLS, response.status(), "Should switch protocols");
        assertEquals(List.of(new User("alice")), opened, "Session should have the accepted context");
    }

    @Test
    public void When_UpgradeIsAccepted_Then_SameContextShouldBeAvailableOnMessage() {
        List<User> opened = new ArrayList<>();
        List<User> received = new ArrayList<>();
        EmbeddedChannel channel = upgrade(createServer(opened, received), "alice");

        // Discard the 101 Switching Protocols response
        channel.releaseOutbound();
        Util.sendFromClient(channel, new TextWebSocketFrame("Hello"));

        assertEquals(1, received.size(), "Message should be received");
        assertSame(opened.getFirst(), received.getFirst(), "Message handler should get the context given on upgrade");
    }
}
