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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpgradeHandlerTest {

    private record User(String name) {
    }

    private final List<User> opened = new ArrayList<>();
    private final List<User> received = new ArrayList<>();
    private WebSocketServer<String, User> server;
    private UpgradeRequest lastUpgradeRequest;

    /**
     * Creates a server accepting upgrades with a cookie, which becomes the session context.
     */
    @BeforeEach
    void setUp() {
        server = new WebSocketServer<String, User>("/chat")
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .onUpgrade((request, response) -> {
                lastUpgradeRequest = request;
                String cookie = request.getCookie("token");

                if (cookie == null) {
                    response
                        .setStatus(401)
                        .setHeader(HttpHeaderNames.WWW_AUTHENTICATE.toString(), "Bearer")
                        .addHeader(HttpHeaderNames.WWW_AUTHENTICATE.toString(), "Basic realm=\"chat\"");
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
        EmbeddedChannel channel = Util.newEmbeddedChannel(new WebSocketServerChannelInitializer<>(server));
        FullHttpRequest request = Util.createHttpRequest("/chat");

        if (cookie != null) {
            request.headers().set(HttpHeaderNames.COOKIE, "token=" + cookie);
        }

        channel.writeInbound(request);
        return channel;
    }

    /**
     * Decodes the response written by the server the way a client does.
     */
    private static HttpResponse readResponse(EmbeddedChannel channel) {
        EmbeddedChannel client = Util.newEmbeddedChannel(new HttpResponseDecoder());
        ByteBuf buffer;

        while ((buffer = channel.readOutbound()) != null) {
            client.writeInbound(buffer);
        }

        return client.readInbound();
    }

    @Test
    void shouldNotOpenSessionWhenUpgradeIsRejected() {
        EmbeddedChannel channel = upgrade(null);

        assertThat(channel.isOpen()).as("Channel should be closed").isFalse();
        assertThat(opened).as("No session should be opened").isEmpty();
        assertThat(server.getConnectedSessions()).as("No session should be registered").isEmpty();
    }

    @Test
    void shouldSendResponseStatusSetByHandlerWhenUpgradeIsRejected() {
        EmbeddedChannel channel = upgrade(null);

        HttpResponse response = readResponse(channel);

        assertThat(response.status()).as("Should send the status set by the upgrade handler").isEqualTo(HttpResponseStatus.UNAUTHORIZED);
        assertThat(response.headers().getAll(HttpHeaderNames.WWW_AUTHENTICATE))
            .as("Should send every header value set by the upgrade handler")
            .containsExactly("Bearer", "Basic realm=\"chat\"");
    }

    @Test
    void shouldMakeContextAvailableOnOpenWhenUpgradeIsAccepted() {
        EmbeddedChannel channel = upgrade("alice");

        HttpResponse response = readResponse(channel);

        assertThat(response.status()).as("Should switch protocols").isEqualTo(HttpResponseStatus.SWITCHING_PROTOCOLS);
        assertThat(opened).as("Session should have the accepted context").isEqualTo(List.of(new User("alice")));
    }

    @Test
    void shouldMakeSameContextAvailableOnMessageWhenUpgradeIsAccepted() {
        EmbeddedChannel channel = upgrade("alice");

        // Discard the 101 Switching Protocols response
        channel.releaseOutbound();
        Util.sendFromClient(channel, new TextWebSocketFrame("Hello"));

        assertThat(received).as("Message should be received").hasSize(1);
        assertThat(received.getFirst()).as("Message handler should get the context given on upgrade").isSameAs(opened.getFirst());
    }

    @Test
    void shouldExposeParsedRequestMetadata() {
        EmbeddedChannel channel = Util.newEmbeddedChannel(new WebSocketServerChannelInitializer<>(server));
        FullHttpRequest request = Util.createHttpRequest("/chat?room=general&tag=a&tag=b");
        request.headers().set(HttpHeaderNames.COOKIE, "token=alice; theme=dark");
        request.headers().add("X-Request-Id", "first");
        request.headers().add("X-Request-Id", "second");

        channel.writeInbound(request);

        assertThat(lastUpgradeRequest.getPath()).isEqualTo("/chat");
        assertThat(lastUpgradeRequest.getQueryParameter("room")).isEqualTo("general");
        assertThat(lastUpgradeRequest.getQueryParameters().get("tag")).containsExactly("a", "b");
        assertThat(lastUpgradeRequest.getCookie("token")).isEqualTo("alice");
        assertThat(lastUpgradeRequest.getCookies()).containsEntry("theme", "dark");
        assertThat(lastUpgradeRequest.getHeader("x-request-id")).isEqualTo("first");
        assertThat(lastUpgradeRequest.getHeaders().get("X-REQUEST-ID")).containsExactly("first", "second");
    }

    @Test
    void shouldExposeRemoteAddress() {
        InetSocketAddress remoteAddress = new InetSocketAddress("127.0.0.1", 12345);
        UpgradeRequest request = new UpgradeRequest("/", Map.of(), Map.of(), Map.of(), remoteAddress);

        assertThat(request.getRemoteAddress()).isSameAs(remoteAddress);
    }

    @Test
    void shouldRejectInvalidResponseStatus() {
        UpgradeResponse response = new UpgradeResponse();

        assertThatThrownBy(() -> response.setStatus(99)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> response.setStatus(600)).isInstanceOf(IllegalArgumentException.class);
        assertThat(response.setStatus(599).getStatus()).isEqualTo(599);
    }

    @Test
    void shouldSetAndAddResponseHeadersCaseInsensitively() {
        UpgradeResponse response = new UpgradeResponse()
            .addHeader("X-Test", "replaced")
            .setHeader("x-test", "first")
            .addHeader("X-TEST", "second");

        assertThat(response.getHeaders()).hasSize(1);
        assertThat(response.getHeaders().get("X-Test")).containsExactly("first", "second");
    }
}
