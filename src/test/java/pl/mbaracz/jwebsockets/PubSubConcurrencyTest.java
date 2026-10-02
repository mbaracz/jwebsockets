package pl.mbaracz.jwebsockets;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class PubSubConcurrencyTest {

    private static final String TOPIC = "topic";
    private static final int THREADS = 8;
    private static final int ITERATIONS = 10_000;

    private static List<WebSocketSession<String, Object>> createSessions(int count, AtomicInteger delivered) {
        return IntStream.range(0, count)
            .mapToObj(_ -> new WebSocketSession<String, Object>(null, (_, _) -> {
                delivered.incrementAndGet();
                return null;
            }))
            .toList();
    }

    /**
     * Runs the task on {@link #THREADS} threads started at the same time and fails if any of them throws.
     */
    private static void runConcurrently(IntConsumer task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<?>> futures = new ArrayList<>();

            for (int thread = 0; thread < THREADS; thread++) {
                int index = thread;
                futures.add(executor.submit(() -> {
                    start.await();
                    task.accept(index);
                    return null;
                }));
            }

            start.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void When_SessionsSubscribeConcurrently_Then_NoSubscriptionShouldBeLost() throws Exception {
        AtomicInteger delivered = new AtomicInteger();
        WebSocketServer<String, Object> server = new WebSocketServer<>();
        List<WebSocketSession<String, Object>> sessions = createSessions(THREADS * 1_000, delivered);

        // Subscribe all sessions to the same topic from multiple threads at once
        runConcurrently(thread -> {
            for (int i = thread; i < sessions.size(); i += THREADS) {
                server.subscribe(sessions.get(i), TOPIC);
            }
        });

        // Publish message
        server.publish(TOPIC, "message");

        // Assert every session is subscribed and received the message
        assertTrue(sessions.stream().allMatch(session -> server.isSubscribed(session, TOPIC)), "All sessions should be subscribed");
        assertEquals(sessions.size(), delivered.get(), "Every subscriber should receive the message");
    }

    @Test
    public void When_MessagesArePublishedDuringSubscriptionChanges_Then_NoExceptionShouldBeThrown() throws Exception {
        WebSocketServer<String, Object> server = new WebSocketServer<>();
        List<WebSocketSession<String, Object>> sessions = createSessions(THREADS, new AtomicInteger());

        // Half of the threads publish while the other half subscribe and unsubscribe
        runConcurrently(thread -> {
            WebSocketSession<String, Object> session = sessions.get(thread);

            for (int i = 0; i < ITERATIONS; i++) {
                if (thread % 2 == 0) {
                    server.publish(TOPIC, "message");
                } else {
                    server.subscribe(session, TOPIC);
                    server.unsubscribe(session, TOPIC);
                }
            }
        });

        // Assert topic was removed after its last subscriber left
        assertFalse(server.getTopics().contains(TOPIC), "Topic should be removed");
    }

    @Test
    public void When_SessionsUnsubscribeConcurrently_Then_OtherSubscriptionsShouldBeKept() throws Exception {
        WebSocketServer<String, Object> server = new WebSocketServer<>();
        List<WebSocketSession<String, Object>> sessions = createSessions(THREADS, new AtomicInteger());

        // Each thread keeps subscribing and unsubscribing its own session,
        // so the topic is repeatedly emptied and recreated by the others
        runConcurrently(thread -> {
            WebSocketSession<String, Object> session = sessions.get(thread);

            for (int i = 0; i < ITERATIONS; i++) {
                server.subscribe(session, TOPIC);
                assertTrue(server.isSubscribed(session, TOPIC), "Subscription should not be lost");
                server.unsubscribe(session, TOPIC);
            }
        });
    }
}
