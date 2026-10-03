package pl.mbaracz.jwebsockets;

import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.configuration.WebSocketServerConfiguration;
import pl.mbaracz.jwebsockets.handler.UpgradeResult;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ServerReconfigurationTest {

    private static WebSocketServer<String, Object> createServer() {
        return new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            );
    }

    @Test
    public void When_ServerIsRunning_Then_ConfigureShouldThrow() {
        WebSocketServer<String, Object> server = createServer();
        server.listen(0);

        try {
            assertThrows(IllegalStateException.class, () -> server.configure(configurer -> configurer.setRespondWithBinaryFrame(true)));
            assertFalse(server.getConfiguration().isRespondWithBinaryFrame(), "Configuration should not change");
        } finally {
            server.stop();
        }
    }

    @Test
    public void When_ServerIsRunning_Then_SettingHandlersShouldThrow() {
        WebSocketServer<String, Object> server = createServer();
        server.listen(0);

        try {
            assertAll(
                () -> assertThrows(IllegalStateException.class, () -> server.onMessage((_, _) -> {})),
                () -> assertThrows(IllegalStateException.class, () -> server.onOpen(_ -> {})),
                () -> assertThrows(IllegalStateException.class, () -> server.onClose((_, _, _) -> {})),
                () -> assertThrows(IllegalStateException.class, () -> server.onUpgrade((_, _) -> UpgradeResult.accept(null))),
                () -> assertThrows(IllegalStateException.class, () -> server.onWritabilityChanged((_, _) -> {})),
                () -> assertThrows(IllegalStateException.class, () -> server.topicBroker(new InMemoryTopicBroker<>()))
            );
        } finally {
            server.stop();
        }
    }

    @Test
    public void When_ServerIsStopped_Then_ItCanBeReconfiguredAndStartedAgain() {
        WebSocketServer<String, Object> server = createServer();
        server.listen(0);
        server.stop();

        // Reconfigure the stopped server before starting it again
        server.configure(configurer -> configurer.setRespondWithBinaryFrame(true))
            .onMessage((_, _) -> {});
        server.listen(0);

        try {
            assertTrue(server.isRunning(), "Server should be running");
            assertTrue(server.getConfiguration().isRespondWithBinaryFrame(), "Configuration should change");
        } finally {
            server.stop();
        }
    }

    @Test
    public void When_CapturedConfigurationIsModifiedWhileRunning_Then_ActiveConfigurationShouldNotChange() {
        AtomicReference<WebSocketServerConfiguration<String>> captured = new AtomicReference<>();
        WebSocketServer<String, Object> server = createServer().configure(captured::set);
        server.listen(0);

        try {
            // Change the configuration through a reference kept from configure()
            captured.get().setMaxMessageSize(123);

            assertEquals(1024 * 1024, server.getConfiguration().getMaxMessageSize(), "Running server should keep its configuration");
        } finally {
            server.stop();
        }
    }

    @Test
    public void When_AllowedOriginsListIsModifiedWhileRunning_Then_ActiveConfigurationShouldNotChange() {
        List<String> origins = new ArrayList<>(List.of("http://example.com"));
        WebSocketServer<String, Object> server = createServer().configure(configurer -> configurer.setAllowedOrigins(origins));
        server.listen(0);

        try {
            // Change the list passed to setAllowedOrigins()
            origins.add("http://other.example.com");

            assertEquals(List.of("http://example.com"), server.getConfiguration().getAllowedOrigins(), "Running server should keep its allowed origins");
        } finally {
            server.stop();
        }
    }

    @Test
    public void When_AllowedOriginsListIsModifiedBeforeStartup_Then_ConfigurationShouldNotChange() {
        List<String> origins = new ArrayList<>(List.of("http://example.com"));
        WebSocketServer<String, Object> server = createServer().configure(configurer -> configurer.setAllowedOrigins(origins));

        // Change the list passed to setAllowedOrigins() before the server is started
        origins.add("http://other.example.com");

        assertEquals(List.of("http://example.com"), server.getConfiguration().getAllowedOrigins(), "Configuration should keep its allowed origins");
    }
}
