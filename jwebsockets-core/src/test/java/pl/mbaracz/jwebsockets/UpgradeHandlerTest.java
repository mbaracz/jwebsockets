package pl.mbaracz.jwebsockets;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.handler.UpgradeResult;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UpgradeHandlerTest {

    private record User(String name) {
    }

    private final List<User> opened = new ArrayList<>();
    private final List<User> received = new ArrayList<>();
    private WebSocketServer<String, User> server;

    /**
     * Creates a server accepting upgrades with a cookie, which becomes the session context.
     */
    @BeforeEach
    public void setUp() {
        server = new WebSocketServer<String, User>()
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
    private EmbeddedChannel upgrade(String cookie) {
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
    public void shouldNotOpenSessionWhenUpgradeIsRejected() {
        EmbeddedChannel channel = upgrade(null);

        assertThat(channel.isOpen()).as("Channel should be closed").isFalse();
        assertThat(opened).as("No session should be opened").isEmpty();
        assertThat(server.getConnectedSessions()).as("No session should be registered").isEmpty();
    }

    @Test
    public void shouldSendResponseStatusSetByHandlerWhenUpgradeIsRejected() {
        EmbeddedChannel channel = upgrade(null);

        HttpResponse response = readResponse(channel);

        assertThat(response.status()).as("Should send the status set by the upgrade handler").isEqualTo(HttpResponseStatus.UNAUTHORIZED);
    }

    @Test
    public void shouldMakeContextAvailableOnOpenWhenUpgradeIsAccepted() {
        EmbeddedChannel channel = upgrade("alice");

        HttpResponse response = readResponse(channel);

        assertThat(response.status()).as("Should switch protocols").isEqualTo(HttpResponseStatus.SWITCHING_PROTOCOLS);
        assertThat(opened).as("Session should have the accepted context").isEqualTo(List.of(new User("alice")));
    }

    @Test
    public void shouldMakeSameContextAvailableOnMessageWhenUpgradeIsAccepted() {
        EmbeddedChannel channel = upgrade("alice");

        // Discard the 101 Switching Protocols response
        channel.releaseOutbound();
        Util.sendFromClient(channel, new TextWebSocketFrame("Hello"));

        assertThat(received).as("Message should be received").hasSize(1);
        assertThat(received.getFirst()).as("Message handler should get the context given on upgrade").isSameAs(opened.getFirst());
    }
}
