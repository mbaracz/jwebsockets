package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class TopicBrokerTest {

    /**
     * Records the calls made by the server instead of keeping subscriptions.
     */
    private static final class RecordingTopicBroker implements TopicBroker<String, Object> {

        private final List<String> calls = new ArrayList<>();
        private final List<WebSocketSession<String, Object>> sessions = new ArrayList<>();

        private void record(String call, WebSocketSession<String, Object> session) {
            calls.add(call);
            sessions.add(session);
        }

        @Override
        public void subscribe(String topic, WebSocketSession<String, Object> session) {
            record("subscribe " + topic, session);
        }

        @Override
        public boolean isSubscribed(String topic, WebSocketSession<String, Object> session) {
            record("isSubscribed " + topic, session);
            return true;
        }

        @Override
        public void unsubscribe(String topic, WebSocketSession<String, Object> session) {
            record("unsubscribe " + topic, session);
        }

        @Override
        public void unsubscribeAll(WebSocketSession<String, Object> session) {
            record("unsubscribeAll", session);
        }

        @Override
        public void clear() {
            calls.add("clear");
        }

        @Override
        public void publish(String topic, String message) {
            calls.add("publish " + topic + " " + message);
        }

        @Override
        public Set<String> getTopics() {
            calls.add("getTopics");
            return Set.of("brokered");
        }
    }

    private final RecordingTopicBroker broker = new RecordingTopicBroker();
    private WebSocketServer<String, Object> server;

    @BeforeEach
    public void setUp() {
        server = new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .topicBroker(broker);
    }

    @Test
    public void shouldDelegatePubSubToCustomBrokerWhenItIsSet() {
        WebSocketSession<String, Object> session = new WebSocketSession<>(null, null, null);

        server.subscribe(session, "topic");
        boolean subscribed = server.isSubscribed(session, "topic");
        server.unsubscribe(session, "topic");
        server.publish("topic", "Hello");
        Set<String> topics = server.getTopics();
        server.unsubscribeAllTopics();

        assertThat(broker.calls)
            .as("Server should delegate every call to the broker")
            .isEqualTo(List.of("subscribe topic", "isSubscribed topic", "unsubscribe topic", "publish topic Hello", "getTopics", "clear"));
        assertThat(broker.sessions).as("Broker should get the session").isEqualTo(List.of(session, session, session));
        assertThat(subscribed).as("Server should return the answer of the broker").isTrue();
        assertThat(topics).as("Server should return the topics of the broker").isEqualTo(Set.of("brokered"));
    }

    @Test
    public void shouldUnsubscribeSessionFromCustomBrokerWhenItDisconnects() {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        server.onOpen(opened::add);

        EmbeddedChannel channel = Util.connect(server);

        channel.close();

        assertThat(broker.calls).as("Disconnect should unsubscribe the session from all topics").isEqualTo(List.of("unsubscribeAll"));
        assertThat(broker.sessions).as("Broker should get the disconnected session").isEqualTo(opened);
    }
}
