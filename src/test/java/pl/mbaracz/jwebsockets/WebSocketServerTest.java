package pl.mbaracz.jwebsockets;

import org.junit.jupiter.api.*;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

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
}
