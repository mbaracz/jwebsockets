package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SessionPubSubConcurrencyTest {

    private static final String TOPIC = "topic";

    private interface PubSubOperation {
        void run(WebSocketServer<String, Object> server, WebSocketSession<String, Object> session);
    }

    private static final class PausingTopicBroker implements TopicBroker<String, Object> {

        private final InMemoryTopicBroker<String, Object> delegate = new InMemoryTopicBroker<>();
        private final CountDownLatch sessionRemovedFromTopics = new CountDownLatch(1);
        private final CountDownLatch continueDisconnect = new CountDownLatch(1);

        @Override
        public void subscribe(String topic, WebSocketSession<String, Object> session) {
            delegate.subscribe(topic, session);
        }

        @Override
        public boolean isSubscribed(String topic, WebSocketSession<String, Object> session) {
            return delegate.isSubscribed(topic, session);
        }

        @Override
        public void unsubscribe(String topic, WebSocketSession<String, Object> session) {
            delegate.unsubscribe(topic, session);
        }

        @Override
        public void unsubscribeAll(WebSocketSession<String, Object> session) {
            delegate.unsubscribeAll(session);
            sessionRemovedFromTopics.countDown();

            try {
                if (!continueDisconnect.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out while pausing disconnect");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while pausing disconnect", exception);
            }
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public void publish(String topic, String message) {
            delegate.publish(topic, message);
        }

        @Override
        public Set<String> getTopics() {
            return delegate.getTopics();
        }
    }

    @Test
    @Timeout(10)
    void shouldNotRestoreSubscriptionWhenSubscribeRacesWithDisconnect() throws Exception {
        Throwable failure = runRace((server, session) -> server.subscribe(session, TOPIC));

        assertThat(failure)
            .as("Subscribe should reject the session once disconnect has removed it")
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("WebSocket session is not connected");
    }

    @Test
    @Timeout(10)
    void shouldKeepRegistriesEmptyWhenUnsubscribeRacesWithDisconnect() throws Exception {
        Throwable failure = runRace((server, session) -> server.unsubscribe(session, TOPIC));

        assertThat(failure).isNull();
    }

    @Test
    @Timeout(10)
    void shouldKeepRegistriesEmptyWhenPublishRacesWithDisconnect() throws Exception {
        Throwable failure = runRace((server, _) -> server.publish(TOPIC, "message"));

        assertThat(failure).isNull();
    }

    private static Throwable runRace(PubSubOperation operation) throws Exception {
        PausingTopicBroker broker = new PausingTopicBroker();
        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .topicBroker(broker);
        EmbeddedChannel channel = Util.connect(server);
        WebSocketSession<String, Object> session = server.getSessionByChannelId(channel.id());
        server.subscribe(session, TOPIC);

        AtomicReference<Throwable> disconnectFailure = new AtomicReference<>();
        Thread disconnect = Thread.ofPlatform().start(() -> {
            try {
                channel.close();
            } catch (Throwable throwable) {
                disconnectFailure.set(throwable);
            }
        });

        assertThat(broker.sessionRemovedFromTopics.await(5, TimeUnit.SECONDS))
            .as("Disconnect should reach topic cleanup")
            .isTrue();

        AtomicReference<Throwable> operationFailure = new AtomicReference<>();
        CountDownLatch operationStarted = new CountDownLatch(1);
        Thread concurrentOperation = Thread.ofPlatform().start(() -> {
            operationStarted.countDown();
            try {
                operation.run(server, session);
            } catch (Throwable throwable) {
                operationFailure.set(throwable);
            }
        });

        assertThat(operationStarted.await(5, TimeUnit.SECONDS)).isTrue();
        waitUntilBlockedOrFinished(concurrentOperation);
        broker.continueDisconnect.countDown();
        disconnect.join();
        concurrentOperation.join();

        assertThat(disconnectFailure.get()).isNull();
        assertThat(server.getConnectedSessions()).as("Session registry should be empty").isEmpty();
        assertThat(broker.isSubscribed(TOPIC, session)).as("Disconnected session should not remain in topic registry").isFalse();
        assertThat(broker.getTopics()).as("Empty topic should be removed").doesNotContain(TOPIC);

        return operationFailure.get();
    }

    private static void waitUntilBlockedOrFinished(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        while (thread.isAlive() && thread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }

        if (thread.isAlive() && thread.getState() != Thread.State.BLOCKED) {
            throw new AssertionError("Concurrent operation did not reach the disconnect race");
        }
    }
}
