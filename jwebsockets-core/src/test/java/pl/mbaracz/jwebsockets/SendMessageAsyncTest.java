package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

class SendMessageAsyncTest {

    private static final int SENDER_THREADS = 12;
    private static final int MESSAGES_PER_SENDER = 100;

    private final List<WebSocketSession<String, Object>> opened = new ArrayList<>();
    private WebSocketServer<String, Object> server;

    @BeforeEach
    void setUp() {
        server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .onOpen(opened::add);
    }

    @Test
    void shouldCompleteSendMessageAsyncSuccessfullyWhenMessageIsWritten() {
        EmbeddedChannel channel = Util.connect(server);

        CompletableFuture<Void> result = opened.getFirst().sendMessageAsync("Hello").toCompletableFuture();

        // Assert stage completed normally and the message was written
        assertThat(result.isDone()).as("Stage should be completed").isTrue();
        assertThat(result.isCompletedExceptionally()).as("Stage should complete normally").isFalse();

        TextWebSocketFrame frame = assertThat(Util.readFromServer(channel)).asInstanceOf(type(TextWebSocketFrame.class)).actual();
        assertThat(frame.text()).as("Message should be written").isEqualTo("Hello");
        frame.release();
    }

    @Test
    @Timeout(20)
    void shouldDeliverEveryMessageWhenOneSessionIsUsedConcurrently() throws Exception {
        AtomicReference<WebSocketSession<String, Object>> openedSession = new AtomicReference<>();
        CountDownLatch sessionOpened = new CountDownLatch(1);
        CountDownLatch messagesReceived = new CountDownLatch(SENDER_THREADS * MESSAGES_PER_SENDER);
        ConcurrentLinkedQueue<String> outboundMessages = new ConcurrentLinkedQueue<>();
        ExecutorService senders = Executors.newFixedThreadPool(SENDER_THREADS);
        ExecutorService clientExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch ready = new CountDownLatch(SENDER_THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<?>[] sendFutures = new CompletableFuture<?>[SENDER_THREADS * MESSAGES_PER_SENDER];
        List<Future<?>> senderTasks = new ArrayList<>(SENDER_THREADS);
        List<String> expectedMessages = new ArrayList<>(SENDER_THREADS * MESSAGES_PER_SENDER);
        WebSocket client = null;

        try {
            server.onOpen(session -> {
                openedSession.set(session);
                sessionOpened.countDown();
            });
            int port = findFreePort();
            server.listen(port);

            client = HttpClient.newBuilder()
                .executor(clientExecutor)
                .build()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port), new WebSocket.Listener() {
                    private final StringBuilder message = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(Long.MAX_VALUE);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        message.append(data);

                        if (last) {
                            outboundMessages.add(message.toString());
                            message.setLength(0);
                            messagesReceived.countDown();
                        }

                        return null;
                    }
                })
                .get(5, TimeUnit.SECONDS);

            assertThat(sessionOpened.await(5, TimeUnit.SECONDS)).as("The session should open").isTrue();
            WebSocketSession<String, Object> session = openedSession.get();

            for (int sender = 0; sender < SENDER_THREADS; sender++) {
                int senderId = sender;

                for (int sequence = 0; sequence < MESSAGES_PER_SENDER; sequence++) {
                    expectedMessages.add(message(senderId, sequence));
                }

                senderTasks.add(senders.submit(() -> {
                    ready.countDown();
                    start.await();

                    for (int sequence = 0; sequence < MESSAGES_PER_SENDER; sequence++) {
                        int index = senderId * MESSAGES_PER_SENDER + sequence;
                        sendFutures[index] = session.sendMessageAsync(message(senderId, sequence)).toCompletableFuture();
                    }

                    return null;
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).as("Every sender should be ready").isTrue();
            start.countDown();

            for (Future<?> senderTask : senderTasks) {
                senderTask.get(5, TimeUnit.SECONDS);
            }

            CompletableFuture.allOf(sendFutures).get(5, TimeUnit.SECONDS);
            assertThat(messagesReceived.await(5, TimeUnit.SECONDS)).as("Every message should reach the client").isTrue();

            assertThat(sendFutures)
                .allMatch(CompletableFuture::isDone)
                .noneMatch(CompletableFuture::isCompletedExceptionally);

            // Preserve the observed global outbound order, but do not make it part of the contract:
            // concurrent callers have no single, well-defined invocation order to compare it with.
            assertThat(outboundMessages).containsExactlyInAnyOrderElementsOf(expectedMessages);
        } finally {
            start.countDown();
            if (client != null) {
                client.abort();
            }
            if (server.isRunning()) {
                server.stop();
            }
            senders.shutdownNow();
            clientExecutor.shutdownNow();
            assertThat(senders.awaitTermination(5, TimeUnit.SECONDS)).as("Sender threads should stop").isTrue();
            assertThat(clientExecutor.awaitTermination(5, TimeUnit.SECONDS)).as("Client thread should stop").isTrue();
        }
    }

    @Test
    void shouldCompleteSendMessageAsyncExceptionallyWhenWriteFails() {
        EmbeddedChannel channel = Util.connect(server);

        // Close the connection before sending
        channel.close();
        CompletableFuture<Void> result = opened.getFirst().sendMessageAsync("Hello").toCompletableFuture();

        // Assert stage failed with the cause of the failed write
        assertThat(result.isCompletedExceptionally()).as("Stage should complete exceptionally").isTrue();

        assertThatThrownBy(result::join)
            .isInstanceOf(CompletionException.class)
            .as("Should fail because the channel is closed")
            .cause()
            .isInstanceOf(ClosedChannelException.class);
    }

    @Test
    void shouldCompleteSendMessageAsyncExceptionallyWhenEncodingFails() {
        IllegalStateException encoderFailure = new IllegalStateException("Encoding failed");
        server.configure(configurer -> configurer.setMessageEncoder(_ -> {
            throw encoderFailure;
        }));

        EmbeddedChannel channel = Util.connect(server);

        // Encoder failures are reported through the stage instead of being thrown
        CompletableFuture<Void> result = opened.getFirst().sendMessageAsync("Hello").toCompletableFuture();

        // Assert stage failed with the encoder exception and nothing was written
        assertThat(result.isCompletedExceptionally()).as("Stage should complete exceptionally").isTrue();

        assertThatThrownBy(result::join)
            .isInstanceOf(CompletionException.class)
            .as("Should fail with the encoder exception")
            .cause()
            .isSameAs(encoderFailure);
        assertThat(Util.readFromServer(channel)).as("Nothing should be written").isNull();
    }

    private static String message(int sender, int sequence) {
        return "sender-" + sender + "-message-" + sequence;
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
