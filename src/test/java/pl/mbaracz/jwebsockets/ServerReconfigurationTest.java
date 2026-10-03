package pl.mbaracz.jwebsockets;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.configuration.WebSocketServerConfiguration;
import pl.mbaracz.jwebsockets.handler.UpgradeResult;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

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
    public void When_BuilderIsModifiedAfterConfigure_Then_ServerConfigurationShouldNotChange() {
        AtomicReference<WebSocketServerConfiguration.Builder<String>> captured = new AtomicReference<>();
        WebSocketServer<String, Object> server = createServer().configure(captured::set);

        // Change the builder kept from configure(), before and while the server is running
        captured.get().setMaxMessageSize(123);
        server.listen(0);

        try {
            captured.get().setMaxMessageSize(456);

            assertEquals(1024 * 1024, server.getConfiguration().getMaxMessageSize(), "Server should keep the configuration built by configure()");
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

    @Test
    public void When_ConfigureIsCalledAgain_Then_OtherSettingsShouldBeKept() throws Exception {
        SslContext sslContext = SslContextBuilder.forClient().build();
        Pattern originPattern = Pattern.compile("^https://example\\.com$");
        Executor callbackExecutor = Runnable::run;

        WebSocketServer<String, Object> server = createServer().configure(configurer -> configurer
            .setAllowTextFrames(false)
            .setAllowBinaryFrames(true)
            .setRespondWithBinaryFrame(true)
            .setSslContext(sslContext)
            .setCloseOnException(true)
            .setHeartbeatInterval(Duration.ofSeconds(30))
            .setHeartbeatTimeout(Duration.ofSeconds(5))
            .setCallbackExecutor(callbackExecutor)
            .setWriteBufferWaterMark(1024, 2048)
            .setUnwritableTimeout(Duration.ofSeconds(20))
            .setAllowedOrigin("https://example.com")
            .setAllowedOrigin(originPattern)
            .setSubprotocols("chat")
        );

        // Change one setting only
        server.configure(configurer -> configurer.setMaxMessageSize(2048));

        WebSocketServerConfiguration<String> configuration = server.getConfiguration();

        assertEquals(2048, configuration.getMaxMessageSize(), "Changed setting should be applied");
        assertAll(
            () -> assertFalse(configuration.isAllowTextFrames(), "allowTextFrames"),
            () -> assertTrue(configuration.isAllowBinaryFrames(), "allowBinaryFrames"),
            () -> assertTrue(configuration.isRespondWithBinaryFrame(), "respondWithBinaryFrame"),
            () -> assertSame(sslContext, configuration.getSslContext(), "sslContext"),
            () -> assertTrue(configuration.isCloseOnException(), "closeOnException"),
            () -> assertEquals(Duration.ofSeconds(30), configuration.getHeartbeatInterval(), "heartbeatInterval"),
            () -> assertEquals(Duration.ofSeconds(5), configuration.getHeartbeatTimeout(), "heartbeatTimeout"),
            () -> assertSame(callbackExecutor, configuration.getCallbackExecutor(), "callbackExecutor"),
            () -> assertEquals(2048, configuration.getWriteBufferWaterMark().high(), "writeBufferWaterMark"),
            () -> assertEquals(Duration.ofSeconds(20), configuration.getUnwritableTimeout(), "unwritableTimeout"),
            () -> assertEquals(List.of("https://example.com"), configuration.getAllowedOrigins(), "allowedOrigins"),
            () -> assertSame(originPattern, configuration.getAllowedOriginPattern(), "allowedOriginPattern"),
            () -> assertEquals(List.of("chat"), configuration.getSubprotocols(), "subprotocols"),
            () -> assertSame(PlainTextMessageEncoder.INSTANCE, configuration.getMessageEncoder(), "messageEncoder"),
            () -> assertSame(PlainTextMessageDecoder.INSTANCE, configuration.getMessageDecoder(), "messageDecoder")
        );
    }

    @Test
    public void When_ServerStarts_Then_ItShouldUseLastBuiltConfiguration() {
        WebSocketServer<String, Object> server = createServer()
            .configure(configurer -> configurer.setMaxMessageSize(2048))
            .configure(configurer -> configurer.setSubprotocols("chat"));
        WebSocketServerConfiguration<String> built = server.getConfiguration();

        server.listen(0);

        try {
            assertSame(built, server.getConfiguration(), "Server should run with the last built configuration");
        } finally {
            server.stop();
        }
    }

    @Test
    public void When_SettingIsInvalid_Then_ConfigureShouldThrowWithoutChangingConfiguration() {
        WebSocketServer<String, Object> server = createServer();

        // The heartbeat interval is set before the invalid size, but the configuration is never built
        assertThrows(IllegalArgumentException.class, () -> server.configure(configurer -> configurer
            .setHeartbeatInterval(Duration.ofSeconds(30))
            .setMaxMessageSize(0)
        ));

        assertNull(server.getConfiguration().getHeartbeatInterval(), "Configuration should not change");
        assertAll(
            () -> assertThrows(IllegalArgumentException.class, () -> server.configure(configurer -> configurer.setHeartbeatInterval(Duration.ZERO))),
            () -> assertThrows(IllegalArgumentException.class, () -> server.configure(configurer -> configurer.setHeartbeatTimeout(null))),
            () -> assertThrows(IllegalArgumentException.class, () -> server.configure(configurer -> configurer.setUnwritableTimeout(Duration.ZERO))),
            () -> assertThrows(IllegalArgumentException.class, () -> server.configure(configurer -> configurer.setWriteBufferWaterMark(2048, 1024)))
        );
    }
}
