package pl.mbaracz.jwebsockets;

import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import static org.junit.jupiter.api.Assertions.*;

public class StartupValidationTest {

    @Test
    public void When_MessageEncoderIsMissing_Then_ListenShouldThrow() {
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer.setMessageDecoder(PlainTextMessageDecoder.INSTANCE));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> server.listen(0));

        assertTrue(exception.getMessage().contains("encoder"), "Should be rejected because of the missing encoder");
        assertFalse(server.isRunning(), "Server should not be running");
    }

    @Test
    public void When_MessageDecoderIsMissing_Then_ListenShouldThrow() {
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer.setMessageEncoder(PlainTextMessageEncoder.INSTANCE));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> server.listen(0));

        assertTrue(exception.getMessage().contains("decoder"), "Should be rejected because of the missing decoder");
        assertFalse(server.isRunning(), "Server should not be running");
    }

    @Test
    public void When_StartupFailedValidation_Then_ServerCanBeConfiguredAndStarted() {
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer.setMessageDecoder(PlainTextMessageDecoder.INSTANCE));

        assertThrows(IllegalStateException.class, () -> server.listen(0));

        // Complete the configuration and start the server again
        server.configure(configurer -> configurer.setMessageEncoder(PlainTextMessageEncoder.INSTANCE));
        server.listen(0);

        try {
            assertTrue(server.isRunning(), "Server should be running");
        } finally {
            server.stop();
        }
    }
}
