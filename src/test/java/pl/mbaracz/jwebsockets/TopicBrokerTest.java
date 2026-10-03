package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

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

    private static WebSocketServer<String, Object> createServer(TopicBroker<String, Object> broker) {
        return new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .topicBroker(broker);
    }

    @Test
    public void When_CustomBrokerIsSet_Then_PubSubShouldBeDelegatedToIt() {
        RecordingTopicBroker broker = new RecordingTopicBroker();
        WebSocketServer<String, Object> server = createServer(broker);
        WebSocketSession<String, Object> session = new WebSocketSession<>(null, null, null);

        server.subscribe(session, "topic");
        boolean subscribed = server.isSubscribed(session, "topic");
        server.unsubscribe(session, "topic");
        server.publish("topic", "Hello");
        Set<String> topics = server.getTopics();
        server.unsubscribeAllTopics();

        assertEquals(
            List.of("subscribe topic", "isSubscribed topic", "unsubscribe topic", "publish topic Hello", "getTopics", "clear"),
            broker.calls,
            "Server should delegate every call to the broker"
        );
        assertEquals(List.of(session, session, session), broker.sessions, "Broker should get the session");
        assertTrue(subscribed, "Server should return the answer of the broker");
        assertEquals(Set.of("brokered"), topics, "Server should return the topics of the broker");
    }

    @Test
    public void When_SessionDisconnects_Then_CustomBrokerShouldUnsubscribeIt() {
        RecordingTopicBroker broker = new RecordingTopicBroker();
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(broker).onOpen(opened::add));

        channel.close();

        assertEquals(List.of("unsubscribeAll"), broker.calls, "Disconnect should unsubscribe the session from all topics");
        assertEquals(opened, broker.sessions, "Broker should get the disconnected session");
    }
}
