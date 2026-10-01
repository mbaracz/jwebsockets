package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import org.junit.jupiter.api.*;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WebSocketServerTest {

    private static WebSocketServer<String, Object> server;

    @BeforeAll
    public static void setUp() {
        server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            );
    }

    @Test
    @Order(1)
    public void When_ServerIsAlreadyRunning_Then_ShouldThrowException() {
        server.listen(8080);

        assertTrue(server.isRunning(), "Server should be running");
        assertThrows(IllegalStateException.class, () -> server.listen(8080), "Should throw exception");

        server.stop();
    }

    @Test
    @Order(2)
    public void When_ServerIsNotRunning_Then_ShouldThrowException() {
        assertFalse(server.isRunning(), "Server should not be running");
        assertThrows(IllegalStateException.class, () -> server.broadcast("foo"), "Should throw exception");
    }

    @Test
    @Order(3)
    public void When_ServerIsAlreadyStopped_Then_ShouldThrowException() {
        assertFalse(server.isRunning(), "Server should not be running");
        assertThrows(IllegalStateException.class, () -> server.stop(), "Should throw exception");
    }

    @Test
    @Order(4)
    public void When_EncoderIsNotProvided_Then_ShouldThrowException() {
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer.setMessageDecoder(PlainTextMessageDecoder.INSTANCE));
        assertThrows(IllegalStateException.class, () -> server.listen(8080), "Should throw exception");
    }

    @Test
    @Order(5)
    public void When_DecoderIsNotProvided_Then_ShouldThrowException() {
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer.setMessageEncoder(PlainTextMessageEncoder.INSTANCE));
        assertThrows(IllegalStateException.class, () -> server.listen(8080), "Should throw exception");
    }

    @Test
    @Order(6)
    public void When_PortIsAlreadyInUse_Then_ShouldThrowException() throws IOException {
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            );

        // Occupy a free port
        try (ServerSocket socket = new ServerSocket(0)) {
            // Assert startup fails instead of returning a server that is not running
            assertThrows(IllegalStateException.class, () -> server.listen(socket.getLocalPort()), "Should throw exception");
            assertFalse(server.isRunning(), "Server should not be running");
        }
    }

    @Test
    @Order(7)
    public void When_ServerIsStopped_Then_ItCanBeStartedAgainOnTheSamePort() throws IOException {
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            );
        int port = findFreePort();

        server.listen(port);
        server.stop();

        // Assert port was released before stop() returned
        assertDoesNotThrow(() -> server.listen(port), "Server should start again on the same port");
        server.stop();
    }

    @Test
    @Order(8)
    public void When_ServerIsStopped_Then_SessionsShouldBeClosed() throws IOException {
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            );
        server.listen(findFreePort());

        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.completeHandshake(channel, "/");

        server.stop();

        // Assert client received a going away close frame and the connection was closed
        CloseWebSocketFrame frame = channel.readOutbound();
        assertNotNull(frame, "Close frame should be sent");
        assertEquals(WebSocketCloseStatus.ENDPOINT_UNAVAILABLE.code(), frame.statusCode(), "Should send going away status");
        assertFalse(channel.isOpen(), "Channel should be closed");
        assertTrue(server.getConnectedSessions().isEmpty(), "Sessions should be removed");
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
