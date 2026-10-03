package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class CallbackExecutorTest {

    private static WebSocketServer<String, Object> createServer(Executor executor) {
        return new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                .setCallbackExecutor(executor)
            );
    }

    private static ExecutorService createExecutor() {
        return Executors.newFixedThreadPool(4, Thread.ofPlatform().name("callback-", 0).factory());
    }

    private static void send(EmbeddedChannel channel, String... messages) {
        for (String message : messages) {
            Util.sendFromClient(channel, new TextWebSocketFrame(message));
        }
    }

    /**
     * Waits for the latch at most the given time, without failing when it is not counted down.
     */
    private static void awaitQuietly(CountDownLatch latch, long millis) {
        try {
            latch.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void When_CallbackExecutorIsConfigured_Then_CallbacksShouldRunOnIt() throws InterruptedException {
        try (ExecutorService executor = createExecutor()) {
            List<String> threads = new CopyOnWriteArrayList<>();
            CountDownLatch done = new CountDownLatch(3);

            WebSocketServer<String, Object> server = createServer(executor)
                .onOpen(_ -> {
                    threads.add(Thread.currentThread().getName());
                    done.countDown();
                })
                .onMessage((_, _) -> {
                    threads.add(Thread.currentThread().getName());
                    done.countDown();
                })
                .onClose((_, _, _) -> {
                    threads.add(Thread.currentThread().getName());
                    done.countDown();
                });

            EmbeddedChannel channel = Util.connect(server);
            send(channel, "Hello");
            channel.close();

            assertTrue(done.await(5, TimeUnit.SECONDS), "Callbacks should be called");
            assertTrue(threads.stream().allMatch(name -> name.startsWith("callback-")), "Callbacks should run on the executor: " + threads);
        }
    }

    @Test
    public void When_MessagesArrive_Then_CallbacksShouldRunInOrder() throws InterruptedException {
        try (ExecutorService executor = createExecutor()) {
            List<String> received = new CopyOnWriteArrayList<>();
            CountDownLatch done = new CountDownLatch(100);

            WebSocketServer<String, Object> server = createServer(executor)
                .onMessage((_, message) -> {
                    received.add(message);
                    done.countDown();
                });

            EmbeddedChannel channel = Util.connect(server);
            List<String> sent = IntStream.range(0, 100).mapToObj(String::valueOf).toList();

            send(channel, sent.toArray(String[]::new));

            assertTrue(done.await(5, TimeUnit.SECONDS), "Callbacks should be called");
            assertEquals(sent, received, "Callbacks should run in the order of the messages");
        }
    }

    @Test
    public void When_FirstCallbackIsSlow_Then_SecondShouldNotOvertakeIt() throws InterruptedException {
        try (ExecutorService executor = createExecutor()) {
            List<String> events = new CopyOnWriteArrayList<>();
            CountDownLatch secondStarted = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);

            WebSocketServer<String, Object> server = createServer(executor)
                .onMessage((_, message) -> {
                    events.add("start " + message);

                    if (message.equals("second")) {
                        secondStarted.countDown();
                    } else {
                        // Give the second callback the time to start, which it must not use
                        awaitQuietly(secondStarted, 200);
                    }

                    events.add("end " + message);
                    done.countDown();
                });

            EmbeddedChannel channel = Util.connect(server);
            send(channel, "first", "second");

            assertTrue(done.await(5, TimeUnit.SECONDS), "Callbacks should be called");
            assertEquals(List.of("start first", "end first", "start second", "end second"), events, "Second callback should wait for the first one");
        }
    }

    @Test
    public void When_CallbackThrows_Then_LaterCallbacksShouldStillRun() throws InterruptedException {
        try (ExecutorService executor = createExecutor()) {
            List<String> received = new CopyOnWriteArrayList<>();
            CountDownLatch done = new CountDownLatch(1);

            WebSocketServer<String, Object> server = createServer(executor)
                .onMessage((_, message) -> {
                    if (message.equals("fail")) {
                        throw new IllegalStateException("Callback failed");
                    }

                    received.add(message);
                    done.countDown();
                });

            EmbeddedChannel channel = Util.connect(server);
            send(channel, "fail", "after");

            assertTrue(done.await(5, TimeUnit.SECONDS), "Callback after the failed one should be called");
            assertEquals(List.of("after"), received, "Message after the failed callback should be received");
        }
    }

    @Test
    public void When_SessionCloses_Then_CloseCallbackShouldRunAfterQueuedMessages() throws InterruptedException {
        try (ExecutorService executor = createExecutor()) {
            List<String> events = new CopyOnWriteArrayList<>();
            CountDownLatch closeStarted = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(3);

            WebSocketServer<String, Object> server = createServer(executor)
                .onMessage((_, message) -> {
                    if (message.equals("first")) {
                        // Give the close callback the time to start, which it must not use
                        awaitQuietly(closeStarted, 200);
                    }

                    events.add("message " + message);
                    done.countDown();
                })
                .onClose((_, _, _) -> {
                    closeStarted.countDown();
                    events.add("close");
                    done.countDown();
                });

            EmbeddedChannel channel = Util.connect(server);
            send(channel, "first", "second");
            channel.close();

            assertTrue(done.await(5, TimeUnit.SECONDS), "Callbacks should be called");
            assertEquals(List.of("message first", "message second", "close"), events, "Close callback should run after the queued messages");
        }
    }

    @Test
    public void When_CallbackExecutorRejectsTask_Then_SubmissionShouldFailWithoutStallingSerialExecutor() {
        AtomicBoolean rejecting = new AtomicBoolean(true);
        List<String> executed = new ArrayList<>();

        // Rejects tasks like a shut down executor until told otherwise, then runs them right away
        SerialExecutor executor = new SerialExecutor(task -> {
            if (rejecting.get()) {
                throw new RejectedExecutionException("Executor is shut down");
            }

            task.run();
        });

        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> executed.add("rejected")));

        rejecting.set(false);
        executor.execute(() -> executed.add("accepted"));

        assertEquals(List.of("accepted"), executed, "Tasks submitted after a rejection should still run");
    }
}
