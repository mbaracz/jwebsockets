package pl.mbaracz.jwebsockets;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import static org.junit.jupiter.api.Assertions.*;

public class UpgradeHandlerTest {

    private static class PerSocketData {
        public String cookie;
    }

    private static final WebSocketServer<String, PerSocketData> server = new WebSocketServer<String, PerSocketData>()
            .configure(configurer -> configurer
                    .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                    .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .onUpgrade((request, session, response) -> {
                String cookie = request.headers().get(HttpHeaderNames.COOKIE);

                if (cookie == null) {
                    response.setStatus(HttpResponseStatus.BAD_REQUEST);
                    return false;
                }
                PerSocketData data = new PerSocketData();
                data.cookie = cookie;
                session.setData(data);
                return true;
            });

    @Test
    public void When_CookieIsNotProvided_Then_ShouldReceiveBadRequest() {
        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.performHandshake(channel, "/");

        // Read outgoing message
        FullHttpResponse response = channel.readOutbound();

        // Assert that response is not null
        assertNotNull(response, "Response should not be null");

        // Assert that we receive bad request response status
        assertEquals(response.status(), HttpResponseStatus.BAD_REQUEST, "Should receive bad request response status");
    }

    @Test
    public void When_CookieIsProvided_Then_SessionDataShouldHaveIt() {
        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        channel.pipeline().addFirst(new HttpServerCodec());

        // Construct and send handshake
        String cookie = "foo";
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        HttpHeaders headers = Util.getDefaultHeaders();
        request.headers().set(headers);
        request.headers().add(HttpHeaderNames.COOKIE, cookie);
        channel.writeInbound(request);

        // Assert connection was upgraded
        Object outboundMessage = channel.readOutbound();
        assertInstanceOf(ByteBuf.class, outboundMessage);
        ByteBuf buffer = (ByteBuf) outboundMessage;
        String responseContent = buffer.toString(CharsetUtil.UTF_8);
        assertTrue(responseContent.contains("101 Switching Protocols"));

        WebSocketSession<String, PerSocketData> session = server.getSessionByChannelId(channel.id());

        // Assert that session is not null
        assertNotNull(session);

        // Assert that session data is not null and cookie in session data is equal to sent cookie
        assertNotNull(session.getData(), "Session data should not be null");
        assertEquals(session.getData().cookie, cookie, "Cookie in session data should be equal to sent");
    }
}
