package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class RegistrySnapshotTest {

    private static WebSocketServer<String, Object> createServer() {
        return new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            );
    }

    @Test
    public void When_SubscriptionsChange_Then_ReturnedTopicsShouldNotChange() {
        WebSocketServer<String, Object> server = createServer();
        WebSocketSession<String, Object> session = new WebSocketSession<>(null, null);

        server.subscribe(session, "first");
        Set<String> topics = server.getTopics();

        // Change the subscriptions after the topics were returned
        server.subscribe(session, "second");
        server.unsubscribe(session, "first");

        // Assert returned topics are a snapshot, while a new call sees the change
        assertEquals(Set.of("first"), topics, "Returned topics should not change");
        assertEquals(Set.of("second"), server.getTopics(), "New call should return current topics");
    }

    @Test
    public void When_ReturnedTopicsAreModified_Then_SubscriptionsShouldNotChange() {
        WebSocketServer<String, Object> server = createServer();
        WebSocketSession<String, Object> session = new WebSocketSession<>(null, null);

        server.subscribe(session, "topic");

        // Try to remove the topic through the returned set
        assertThrows(UnsupportedOperationException.class, () -> server.getTopics().remove("topic"));

        // Assert subscription was not removed
        assertTrue(server.isSubscribed(session, "topic"), "Session should stay subscribed");
    }

    @Test
    public void When_SessionsConnectOrDisconnect_Then_ReturnedSessionsShouldNotChange() {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        WebSocketServer<String, Object> server = createServer().onOpen(opened::add);

        EmbeddedChannel firstChannel = Util.connect(server);
        Collection<WebSocketSession<String, Object>> sessions = server.getConnectedSessions();

        // Disconnect the first session and connect a second one after the sessions were returned.
        // Embedded channels share one channel id, so both sessions must not be connected at the same time.
        firstChannel.close();
        Util.connect(server);

        // Assert returned sessions are a snapshot, while a new call sees the change
        assertEquals(List.of(opened.getFirst()), List.copyOf(sessions), "Returned sessions should not change");
        assertEquals(List.of(opened.getLast()), List.copyOf(server.getConnectedSessions()), "New call should return current sessions");
    }
}
