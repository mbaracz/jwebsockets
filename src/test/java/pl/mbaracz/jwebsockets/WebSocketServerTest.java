package pl.mbaracz.jwebsockets;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import org.junit.jupiter.api.*;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assumptions.assumingThat;

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
        assertFalse(server.isRunning(), "Server should not be running after stop");
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

    @Test
    @Order(9)
    public void When_PortIsInvalid_Then_FailedStartupShouldReleaseResources() throws IOException {
        // Open files can only be counted where /proc is available
        Path openFiles = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(openFiles), "Counting open files requires /proc");

        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            );

        // Fail once first, so one-time initialization is not counted as leaked files
        assertThrows(IllegalArgumentException.class, () -> server.listen(-1), "Should reject invalid port");
        long openBefore = countFiles(openFiles);

        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalArgumentException.class, () -> server.listen(-1), "Should reject invalid port");
        }

        long leaked = countFiles(openFiles) - openBefore;

        // Assert each failed startup released its event loops, which hold open selectors
        assertFalse(server.isRunning(), "Server should not be running");
        assertTrue(leaked < 5, "Failed startups should not leave files open, but left " + leaked);
    }

    @Test
    @Order(10)
    public void When_StartupFails_Then_ServerCanBeStartedLater() throws IOException {
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            );

        int port = findFreePort();
        long openBefore = countOpenFiles();

        try {
            // Fail to start while another socket holds the port
            try (ServerSocket _ = new ServerSocket(port)) {
                assertThrows(
                    IllegalStateException.class,
                    () -> server.listen(port),
                    "Should fail while the port is taken"
                );
            }

            long openAfter = countOpenFiles();

            // Assert the failed startup released its event loops, where open files can be counted
            assertFalse(server.isRunning(), "Server should not be running");
            assumingThat(
                openBefore >= 0,
                () -> assertTrue(
                    openAfter - openBefore < 5,
                    "Failed startup should not leave files open"
                )
            );

            // Assert the server starts once the port is free again
            server.listen(port);
            assertTrue(server.isRunning(), "Server should be running");
        } finally {
            stopIfNeeded(server);
        }
    }

    @Test
    @Order(11)
    public void When_ServerChannelClosesUnexpectedly_Then_StopShouldStillReleaseResources() throws Exception {
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            );

        int port = findFreePort();
        long openBefore = countOpenFiles();

        EventLoopGroup bossGroup;
        EventLoopGroup workerGroup;

        try {
            server.listen(port);

            bossGroup = getEventLoopGroup(server, "bossGroup");
            workerGroup = getEventLoopGroup(server, "workerGroup");

            // Close the server channel without stop(), as if it failed
            Channel serverChannel = getServerChannel(server);
            CountDownLatch closed = new CountDownLatch(1);

            serverChannel.closeFuture().addListener(_ -> closed.countDown());
            serverChannel.close();

            assertTrue(
                closed.await(5, TimeUnit.SECONDS),
                "Server channel should be closed"
            );

            // Assert the server is no longer accepting connections
            assertFalse(server.isRunning(), "Server should not be running");

            // stop() must still clean up event loop resources
            assertDoesNotThrow(
                server::stop,
                "Stop should release the remaining resources"
            );

            assertTrue(bossGroup.isTerminated(), "Boss event loop group should be terminated");
            assertTrue(workerGroup.isTerminated(), "Worker event loop group should be terminated");

            long openAfter = countOpenFiles();

            assumingThat(
                openBefore >= 0,
                () -> assertTrue(
                    openAfter - openBefore < 5,
                    "Stop should not leave files open"
                )
            );

            // Assert the server can be started again
            server.listen(port);
            assertTrue(server.isRunning(), "Server should be running");
        } finally {
            stopIfNeeded(server);
        }
    }

    @Test
    @Order(12)
    public void When_ListenIsCalledWhileStopping_Then_ShouldThrowException() throws Exception {
        CountDownLatch handlerEntered = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);

        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .onMessage((_, _) -> {
                handlerEntered.countDown();

                try {
                    releaseHandler.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });

        int port = findFreePort();
        WebSocket client = null;
        FutureTask<Void> stopper = null;

        try {
            server.listen(port);

            // Keep a worker event loop busy in the message handler,
            // so stop() cannot complete immediately
            client = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(
                    URI.create("ws://localhost:" + port + "/"),
                    new WebSocket.Listener() {
                    }
                )
                .join();

            client.sendText("block", true).join();

            assertTrue(
                handlerEntered.await(5, TimeUnit.SECONDS),
                "Message handler should be called"
            );

            stopper = new FutureTask<>(() -> {
                server.stop();
                return null;
            });

            Thread.ofPlatform().start(stopper);

            awaitCondition(
                () -> !server.isRunning(),
                "Server should start stopping"
            );

            // Assert a new startup is rejected while the server is still stopping
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> server.listen(port),
                "Should not start while stopping"
            );

            assertTrue(
                exception.getMessage().contains("stopping"),
                "Should be rejected because the server is stopping"
            );

            // Allow stop() to finish
            releaseHandler.countDown();

            // get() propagates exceptions thrown by stop()
            stopper.get(15, TimeUnit.SECONDS);

            // Assert the server can be started again once stopping completed
            server.listen(port);
            assertTrue(server.isRunning(), "Server should be running");
        } finally {
            releaseHandler.countDown();

            if (client != null) {
                client.abort();
            }

            // If stop() was started but an assertion failed before get(),
            // still wait for it after unblocking the handler.
            if (stopper != null && !stopper.isDone()) {
                try {
                    stopper.get(15, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // The main test path verifies stop() failures.
                    // This branch exists only to avoid leaking the test thread.
                }
            }

            stopIfNeeded(server);
        }
    }

    private static EventLoopGroup getEventLoopGroup(
        WebSocketServer<?, ?> server,
        String fieldName
    ) throws ReflectiveOperationException {
        Field field = WebSocketServer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (EventLoopGroup) field.get(server);
    }

    private static void stopIfNeeded(WebSocketServer<?, ?> server) {
        try {
            server.stop();
        } catch (IllegalStateException ignored) {
            // Already stopped, used only for test cleanup.
        }
    }

    /**
     * Returns the server channel, to simulate it closing without stop().
     */
    private static Channel getServerChannel(WebSocketServer<?, ?> server) throws ReflectiveOperationException {
        Field field = WebSocketServer.class.getDeclaredField("serverChannel");
        field.setAccessible(true);
        return (Channel) field.get(server);
    }

    private static void awaitCondition(BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, message);
            Thread.sleep(10);
        }
    }

    /**
     * Counts the files opened by this process, or returns -1 where /proc is not available.
     */
    private static long countOpenFiles() throws IOException {
        Path openFiles = Path.of("/proc/self/fd");
        return Files.isDirectory(openFiles) ? countFiles(openFiles) : -1;
    }

    private static long countFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.count();
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
