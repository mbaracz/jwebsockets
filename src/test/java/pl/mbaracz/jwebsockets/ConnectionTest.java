package pl.mbaracz.jwebsockets;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionTest {

    private static WebSocketServer<String, Object> server;
    private static WebSocketServer<String, Object> customPathServer;
    private static WebSocketServer<String, Object> requireOriginServer;
    private static WebSocketServer<String, Object> requireOriginPatternServer;

    @BeforeAll
    static void setup() {
        server = new WebSocketServer<String, Object>()
                .configure(configurer -> configurer
                        .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                        .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                );

        customPathServer = new WebSocketServer<String, Object>("/foo")
                .configure(configurer -> configurer
                        .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                        .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                );

        requireOriginServer = new WebSocketServer<String, Object>()
                .configure(configurer -> configurer
                        .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                        .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                        .setAllowedOrigin("http://example.com")
                );

        requireOriginPatternServer = new WebSocketServer<String, Object>()
                .configure(configurer -> configurer
                        .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                        .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                        .setAllowedOrigin(Pattern.compile("^(http|https)://example\\.com$"))
                );
    }

    @Nested
    class PathTests {
        @Test
        public void When_PathIsValid_Then_ConnectionShouldBeUpgraded() {
            WebSocketServerHandler<String, Object> handler = new WebSocketServerHandler<>(server);
            EmbeddedChannel channel = new EmbeddedChannel(handler);
            channel.pipeline().addFirst(new HttpServerCodec());

            // Construct http request
            String path = "/";
            FullHttpRequest request = Util.createHttpRequest(path);

            // Send request
            channel.writeInbound(request);

            // Assert connection was upgraded
            Object outboundMessage = channel.readOutbound();
            assertInstanceOf(ByteBuf.class, outboundMessage);
            ByteBuf buffer = (ByteBuf) outboundMessage;
            String responseContent = buffer.toString(CharsetUtil.UTF_8);
            assertTrue(responseContent.contains("101 Switching Protocols"));
        }

        @Test
        public void When_PathIsInvalid_Then_ConnectionShouldBeClosed() {
            EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));

            // Construct http request
            String path = "/foo";
            FullHttpRequest request = Util.createHttpRequest(path);

            // Send request
            channel.writeInbound(request);

            // Read response
            FullHttpResponse response = channel.readOutbound();

            // Assert expected behaviour
            assertEquals(HttpResponseStatus.BAD_REQUEST, response.status(), "Should receive bad request response");
            assertFalse(channel.isOpen(), "Channel should be closed");
            assertFalse(channel.isActive(), "Channel should not be active");
        }

        @Test
        public void When_CustomPathIsValid_Then_ConnectionShouldBeOpened() {
            WebSocketServerHandler<String, Object> handler = new WebSocketServerHandler<>(customPathServer);
            EmbeddedChannel channel = new EmbeddedChannel(handler);

            // Construct http request
            String path = "/foo";
            FullHttpRequest request = Util.createHttpRequest(path);

            // Send request
            channel.writeInbound(request);

            // Assert expected behaviour
            assertTrue(channel.isOpen(), "Channel should be opened");
            assertTrue(channel.isActive(), "Channel should be active");
        }

        @Test
        public void When_CustomPathIsInvalid_Then_ConnectionShouldBeClosed() {
            EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(customPathServer));

            // Construct http request
            String path = "/";
            FullHttpRequest request = Util.createHttpRequest(path);

            // Send request
            channel.writeInbound(request);

            // Read response
            FullHttpResponse response = channel.readOutbound();

            // Assert expected behaviour
            assertEquals(HttpResponseStatus.BAD_REQUEST, response.status(), "Should receive bad request response");
            assertFalse(channel.isOpen(), "Channel should be closed");
            assertFalse(channel.isActive(), "Channel should not be active");
        }
    }

    @Nested
    class OriginTests {
        @Test
        public void When_OriginIsValidViaPattern_Then_ConnectionShouldBeOpened() {
            EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(requireOriginPatternServer));

            // Construct http request
            String path = "/";
            FullHttpRequest request = Util.createHttpRequest(path);

            // Add origin header
            request.headers().add(HttpHeaderNames.ORIGIN, "http://example.com");

            // Send request
            channel.writeInbound(request);

            // Assert expected behavior
            assertTrue(channel.isOpen(), "Channel should be open");
            assertTrue(channel.isActive(), "Channel should be active");
        }

        @Test
        public void When_OriginIsInvalidViaPattern_Then_ConnectionShouldBeForbidden() {
            EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(requireOriginPatternServer));

            // Construct http request
            String path = "/";
            FullHttpRequest request = Util.createHttpRequest(path);

            // Add origin header
            request.headers().add(HttpHeaderNames.ORIGIN, "http://wrong.com");

            // Send request
            channel.writeInbound(request);

            // Assert expected behavior
            FullHttpResponse response = channel.readOutbound();
            assertEquals(HttpResponseStatus.FORBIDDEN, response.status(), "Should receive forbidden response");
            assertFalse(channel.isOpen(), "Channel should be closed");
            assertFalse(channel.isActive(), "Channel should not be active");
        }

        @Test
        public void When_OriginIsValid_Then_ConnectionShouldBeOpened() {
            EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(requireOriginServer));

            // Construct http request
            String path = "/";
            FullHttpRequest request = Util.createHttpRequest(path);

            // Add origin header
            request.headers().add(HttpHeaderNames.ORIGIN, "http://example.com");

            // Send request
            channel.writeInbound(request);

            // Assert expected behavior
            assertTrue(channel.isOpen(), "Channel should be open");
            assertTrue(channel.isActive(), "Channel should be active");
        }

        @Test
        public void When_OriginIsInvalid_Then_ConnectionShouldBeForbidden() {
            EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(requireOriginServer));

            // Construct http request
            String path = "/";
            FullHttpRequest request = Util.createHttpRequest(path);

            // Add origin header
            request.headers().add(HttpHeaderNames.ORIGIN, "http://wrong.com");

            // Send request
            channel.writeInbound(request);

            // Assert expected behavior
            FullHttpResponse response = channel.readOutbound();
            assertEquals(HttpResponseStatus.FORBIDDEN, response.status(), "Should receive forbidden response");
            assertFalse(channel.isOpen(), "Channel should be closed");
            assertFalse(channel.isActive(), "Channel should not be active");
        }

        @Test
        public void When_OriginIsNotProvided_Then_ConnectionShouldBeForbidden() {
            EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(requireOriginServer));

            // Construct http request
            String path = "/";
            FullHttpRequest request = Util.createHttpRequest(path);

            // Send request
            channel.writeInbound(request);

            // Assert expected behavior
            FullHttpResponse response = channel.readOutbound();
            assertEquals(HttpResponseStatus.FORBIDDEN, response.status(), "Should receive forbidden response");
            assertFalse(channel.isOpen(), "Channel should be closed");
            assertFalse(channel.isActive(), "Channel should not be active");
        }
    }
}
