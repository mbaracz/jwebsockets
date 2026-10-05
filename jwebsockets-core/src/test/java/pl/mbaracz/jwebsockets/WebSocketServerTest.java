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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assumptions.assumingThat;

public class WebSocketServerTest {

    private WebSocketServer<String, Object> server;

    @BeforeEach
    public void setUp() {
        server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            );
    }

    @Test
    public void shouldThrowExceptionWhenServerIsAlreadyRunning() {
        server.listen(8080);

        assertThat(server.isRunning()).as("Server should be running").isTrue();
        assertThatThrownBy(() -> server.listen(8080)).as("Should throw exception").isInstanceOf(IllegalStateException.class);

        server.stop();
        assertThat(server.isRunning()).as("Server should not be running after stop").isFalse();
    }

    @Test
    public void shouldThrowExceptionWhenServerIsNotRunning() {
        assertThat(server.isRunning()).as("Server should not be running").isFalse();
        assertThatThrownBy(() -> server.broadcast("foo")).as("Should throw exception").isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void shouldThrowExceptionWhenServerIsAlreadyStopped() throws IOException {
        // Start and stop the server
        server.listen(findFreePort());
        server.stop();

        assertThat(server.isRunning()).as("Server should not be running").isFalse();
        assertThatThrownBy(() -> server.stop()).as("Should throw exception").isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void shouldThrowExceptionWhenEncoderIsNotProvided() {
        server.configure(configurer -> configurer.setMessageEncoder(null));

        assertThatThrownBy(() -> server.listen(8080)).as("Should throw exception").isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void shouldThrowExceptionWhenDecoderIsNotProvided() {
        server.configure(configurer -> configurer.setMessageDecoder(null));

        assertThatThrownBy(() -> server.listen(8080)).as("Should throw exception").isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void shouldThrowExceptionWhenPortIsAlreadyInUse() throws IOException {
        // Occupy a free port
        try (ServerSocket socket = new ServerSocket(0)) {
            // Assert startup fails instead of returning a server that is not running
            assertThatThrownBy(() -> server.listen(socket.getLocalPort())).as("Should throw exception").isInstanceOf(IllegalStateException.class);
            assertThat(server.isRunning()).as("Server should not be running").isFalse();
        }
    }

    @Test
    public void shouldStartAgainOnTheSamePortWhenServerIsStopped() throws IOException {
        int port = findFreePort();

        server.listen(port);
        server.stop();

        // Assert port was released before stop() returned
        assertThatCode(() -> server.listen(port)).as("Server should start again on the same port").doesNotThrowAnyException();
        server.stop();
    }

    @Test
    public void shouldCloseSessionsWhenServerIsStopped() throws IOException {
        server.listen(findFreePort());

        // Construct channel and perform handshake
        EmbeddedChannel channel = Util.newEmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.completeHandshake(channel, "/");

        server.stop();

        // Assert client received a going away close frame and the connection was closed
        CloseWebSocketFrame frame = channel.readOutbound();
        assertThat(frame).as("Close frame should be sent").isNotNull();
        assertThat(frame.statusCode()).as("Should send going away status").isEqualTo(WebSocketCloseStatus.ENDPOINT_UNAVAILABLE.code());
        assertThat(channel.isOpen()).as("Channel should be closed").isFalse();
        assertThat(server.getConnectedSessions()).as("Sessions should be removed").isEmpty();
        frame.release();
    }

    @Test
    public void shouldReleaseResourcesOfFailedStartupWhenPortIsInvalid() throws IOException {
        // Open files can only be counted where /proc is available
        Path openFiles = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(openFiles), "Counting open files requires /proc");

        // Fail once first, so one-time initialization is not counted as leaked files
        assertThatThrownBy(() -> server.listen(-1)).as("Should reject invalid port").isInstanceOf(IllegalArgumentException.class);
        long openBefore = countFiles(openFiles);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> server.listen(-1)).as("Should reject invalid port").isInstanceOf(IllegalArgumentException.class);
        }

        long leaked = countFiles(openFiles) - openBefore;

        // Assert each failed startup released its event loops, which hold open selectors
        assertThat(server.isRunning()).as("Server should not be running").isFalse();
        assertThat(leaked).as("Failed startups should not leave files open").isLessThan(5);
    }

    @Test
    public void shouldStartLaterWhenStartupFails() throws IOException {
        int port = findFreePort();
        long openBefore = countOpenFiles();

        try {
            // Fail to start while another socket holds the port
            try (ServerSocket _ = new ServerSocket(port)) {
                assertThatThrownBy(() -> server.listen(port)).as("Should fail while the port is taken").isInstanceOf(IllegalStateException.class);
            }

            long openAfter = countOpenFiles();

            // Assert the failed startup released its event loops, where open files can be counted
            assertThat(server.isRunning()).as("Server should not be running").isFalse();
            assumingThat(
                openBefore >= 0,
                () -> assertThat(openAfter - openBefore)
                    .as("Failed startup should not leave files open")
                    .isLessThan(5)
            );

            // Assert the server starts once the port is free again
            server.listen(port);
            assertThat(server.isRunning()).as("Server should be running").isTrue();
        } finally {
            stopIfNeeded(server);
        }
    }

    @Test
    public void shouldStillReleaseResourcesOnStopWhenServerChannelClosesUnexpectedly() throws Exception {
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

            assertThat(closed.await(5, TimeUnit.SECONDS)).as("Server channel should be closed").isTrue();

            // Assert the server is no longer accepting connections
            assertThat(server.isRunning()).as("Server should not be running").isFalse();

            // stop() must still clean up event loop resources
            assertThatCode(server::stop).as("Stop should release the remaining resources").doesNotThrowAnyException();

            assertThat(bossGroup.isTerminated()).as("Boss event loop group should be terminated").isTrue();
            assertThat(workerGroup.isTerminated()).as("Worker event loop group should be terminated").isTrue();

            long openAfter = countOpenFiles();

            assumingThat(
                openBefore >= 0,
                () -> assertThat(openAfter - openBefore)
                    .as("Stop should not leave files open")
                    .isLessThan(5)
            );

            // Assert the server can be started again
            server.listen(port);
            assertThat(server.isRunning()).as("Server should be running").isTrue();
        } finally {
            stopIfNeeded(server);
        }
    }

    @Test
    public void shouldThrowExceptionWhenListenIsCalledWhileStopping() throws Exception {
        CountDownLatch handlerEntered = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);

        server.onMessage((_, _) -> {
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

            assertThat(handlerEntered.await(5, TimeUnit.SECONDS)).as("Message handler should be called").isTrue();

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
            assertThatThrownBy(() -> server.listen(port))
                .as("Should not start while stopping")
                .isInstanceOf(IllegalStateException.class)
                .as("Should be rejected because the server is stopping")
                .hasMessageContaining("stopping");

            // Allow stop() to finish
            releaseHandler.countDown();

            // get() propagates exceptions thrown by stop()
            stopper.get(15, TimeUnit.SECONDS);

            // Assert the server can be started again once stopping completed
            server.listen(port);
            assertThat(server.isRunning()).as("Server should be running").isTrue();
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

    @Test
    public void shouldThrowExceptionWhenConfigureIsCalledWhileStopping() throws Exception {
        CountDownLatch handlerEntered = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);

        server.onMessage((_, _) -> {
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

            assertThat(handlerEntered.await(5, TimeUnit.SECONDS)).as("Message handler should be called").isTrue();

            stopper = new FutureTask<>(() -> {
                server.stop();
                return null;
            });

            Thread.ofPlatform().start(stopper);

            awaitCondition(
                () -> !server.isRunning(),
                "Server should start stopping"
            );

            // Assert the configuration cannot be changed while the server is still stopping
            assertThatThrownBy(() -> server.configure(configurer -> configurer.setRespondWithBinaryFrame(true)))
                .as("Should not be reconfigured while stopping")
                .isInstanceOf(IllegalStateException.class);

            assertThat(server.getConfiguration().isRespondWithBinaryFrame()).as("Configuration should not change").isFalse();

            // Allow stop() to finish
            releaseHandler.countDown();

            // get() propagates exceptions thrown by stop()
            stopper.get(15, TimeUnit.SECONDS);
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
            assertThat(System.nanoTime()).as(message).isLessThan(deadline);
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
