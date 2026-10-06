package pl.mbaracz.jwebsockets;

import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;

class PubSubConcurrencyTest {

    private static final String TOPIC = "topic";
    private static final int THREADS = 8;
    private static final int ITERATIONS = 10_000;

    private InMemoryTopicBroker<String, Object> broker;

    @BeforeEach
    void setUp() {
        broker = new InMemoryTopicBroker<>();
    }

    private static List<WebSocketSession<String, Object>> createSessions(int count, AtomicInteger delivered) {
        return IntStream.range(0, count)
            .mapToObj(_ -> new WebSocketSession<String, Object>(null, (_, _) -> {
                delivered.incrementAndGet();
                return null;
            }, null, null))
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
    void shouldNotLoseSubscriptionWhenSessionsSubscribeConcurrently() throws Exception {
        AtomicInteger delivered = new AtomicInteger();
        List<WebSocketSession<String, Object>> sessions = createSessions(THREADS * 1_000, delivered);

        // Subscribe all sessions to the same topic from multiple threads at once
        runConcurrently(thread -> {
            for (int i = thread; i < sessions.size(); i += THREADS) {
                broker.subscribe(TOPIC, sessions.get(i));
            }
        });

        // Publish message
        broker.publish(TOPIC, "message");

        // Assert every session is subscribed and received the message
        assertThat(sessions).as("All sessions should be subscribed").allMatch(session -> broker.isSubscribed(TOPIC, session));
        assertThat(delivered).as("Every subscriber should receive the message").hasValue(sessions.size());
    }

    @Test
    void shouldNotThrowExceptionWhenMessagesArePublishedDuringSubscriptionChanges() throws Exception {
        List<WebSocketSession<String, Object>> sessions = createSessions(THREADS, new AtomicInteger());

        // Half of the threads publish while the other half subscribe and unsubscribe
        runConcurrently(thread -> {
            WebSocketSession<String, Object> session = sessions.get(thread);

            for (int i = 0; i < ITERATIONS; i++) {
                if (thread % 2 == 0) {
                    broker.publish(TOPIC, "message");
                } else {
                    broker.subscribe(TOPIC, session);
                    broker.unsubscribe(TOPIC, session);
                }
            }
        });

        // Assert topic was removed after its last subscriber left
        assertThat(broker.getTopics()).as("Topic should be removed").doesNotContain(TOPIC);
    }

    @Test
    void shouldKeepOtherSubscriptionsWhenSessionsUnsubscribeConcurrently() throws Exception {
        List<WebSocketSession<String, Object>> sessions = createSessions(THREADS, new AtomicInteger());

        // Each thread keeps subscribing and unsubscribing its own session,
        // so the topic is repeatedly emptied and recreated by the others
        runConcurrently(thread -> {
            WebSocketSession<String, Object> session = sessions.get(thread);

            for (int i = 0; i < ITERATIONS; i++) {
                broker.subscribe(TOPIC, session);
                assertThat(broker.isSubscribed(TOPIC, session)).as("Subscription should not be lost").isTrue();
                broker.unsubscribe(TOPIC, session);
            }
        });
    }
}
