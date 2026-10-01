package pl.mbaracz.jwebsockets;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import static org.junit.jupiter.api.Assertions.*;

public class HandshakeTest {

    private static WebSocketServer<String, Object> createServer(String path) {
        return new WebSocketServer<String, Object>(path)
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            );
    }

    /**
     * Sends the request through an HTTP codec and decodes the response the way a client does.
     */
    private static HttpResponse sendRequest(WebSocketServer<String, Object> server, FullHttpRequest request) {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerCodec(), new WebSocketServerHandler<>(server));
        channel.writeInbound(request);

        EmbeddedChannel client = new EmbeddedChannel(new HttpResponseDecoder());
        ByteBuf buffer;

        while ((buffer = channel.readOutbound()) != null) {
            client.writeInbound(buffer);
        }

        return client.readInbound();
    }

    @Test
    public void When_UpgradeHeaderHasDifferentCase_Then_ConnectionShouldBeUpgraded() {
        FullHttpRequest request = Util.createHttpRequest("/");
        request.headers().set(HttpHeaderNames.UPGRADE, "WebSocket");

        HttpResponse response = sendRequest(createServer("/"), request);

        // Assert upgrade token is compared case-insensitively
        assertEquals(HttpResponseStatus.SWITCHING_PROTOCOLS, response.status(), "Should switch protocols");
    }

    @Test
    public void When_UpgradeHeaderDoesNotContainWebSocket_Then_ShouldReceiveBadRequest() {
        FullHttpRequest request = Util.createHttpRequest("/");
        request.headers().set(HttpHeaderNames.UPGRADE, "h2c");

        HttpResponse response = sendRequest(createServer("/"), request);

        // Assert request is rejected
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status(), "Should receive bad request response");
    }

    @Test
    public void When_ConnectionHeaderHasMultipleTokens_Then_ConnectionShouldBeUpgraded() {
        // Firefox sends the upgrade token together with keep-alive
        FullHttpRequest request = Util.createHttpRequest("/");
        request.headers().set(HttpHeaderNames.CONNECTION, "keep-alive, Upgrade");

        HttpResponse response = sendRequest(createServer("/"), request);

        // Assert upgrade token is found in the token list
        assertEquals(HttpResponseStatus.SWITCHING_PROTOCOLS, response.status(), "Should switch protocols");
    }

    @Test
    public void When_ConnectionHeaderHasNoUpgradeToken_Then_ShouldReceiveBadRequest() {
        FullHttpRequest request = Util.createHttpRequest("/");
        request.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");

        HttpResponse response = sendRequest(createServer("/"), request);

        // Assert request is rejected before the handshake
        assertNotNull(response, "Should receive a response");
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status(), "Should receive bad request response");
    }

    @Test
    public void When_PathHasQueryString_Then_ConnectionShouldBeUpgraded() {
        FullHttpRequest request = Util.createHttpRequest("/chat?token=abc");

        HttpResponse response = sendRequest(createServer("/chat"), request);

        // Assert endpoint is matched by path only
        assertEquals(HttpResponseStatus.SWITCHING_PROTOCOLS, response.status(), "Should switch protocols");
    }

    @Test
    public void When_PathWithQueryStringDoesNotMatch_Then_ShouldReceiveBadRequest() {
        FullHttpRequest request = Util.createHttpRequest("/other?path=/chat");

        HttpResponse response = sendRequest(createServer("/chat"), request);

        // Assert query string does not affect matching
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status(), "Should receive bad request response");
    }

    @Test
    public void When_RequestUriIsMalformed_Then_ShouldReceiveBadRequest() {
        FullHttpRequest request = Util.createHttpRequest("/chat?filter={name}");

        HttpResponse response = sendRequest(createServer("/chat"), request);

        // Assert malformed URI is rejected instead of failing the handler
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status(), "Should receive bad request response");
    }

    @Test
    public void When_LegacyClientConnects_Then_LocationShouldContainEndpointPath() {
        // Requests without a WebSocket version use the legacy handshake, the only one that sends the location
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/chat");
        request.headers()
            .set(HttpHeaderNames.HOST, "localhost:8080")
            .set(HttpHeaderNames.UPGRADE, "WebSocket")
            .set(HttpHeaderNames.CONNECTION, "Upgrade")
            .set(HttpHeaderNames.ORIGIN, "http://localhost:8080");

        HttpResponse response = sendRequest(createServer("/chat"), request);

        // Assert location points to the endpoint path
        assertEquals(HttpResponseStatus.SWITCHING_PROTOCOLS, response.status(), "Should switch protocols");
        assertEquals("ws://localhost:8080/chat", response.headers().get(HttpHeaderNames.WEBSOCKET_LOCATION), "Location should contain the path");
    }
}
