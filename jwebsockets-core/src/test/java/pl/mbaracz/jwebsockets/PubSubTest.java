package pl.mbaracz.jwebsockets;

import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import static org.junit.jupiter.api.Assertions.*;

public class PubSubTest {

    private static final WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
        .configure(configurer -> configurer
            .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
            .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
        );

    @BeforeAll
    public static void setUp() {
        server.listen(8085);
    }

    @Test
    @Order(1)
    public void When_UserIsSubscribed_And_MessageIsPublished_Then_ExpectMessage() {
        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.completeHandshake(channel, "/");

        // Get session from channel id
        WebSocketSession<String, Object> session = server.getSessionByChannelId(channel.id());

        // Assert session is not null
        assertNotNull(session, "Session should not be null");

        // Subscribe to topic
        String topic = "topic-1";
        server.subscribe(session, topic);

        // Publish message
        String message = "topic-1-message";
        server.publish(topic, message);

        // Read outgoing frame
        TextWebSocketFrame textWebSocketFrame = channel.readOutbound();
        String outputMessage = textWebSocketFrame.text();

        // Assert messages are equal
        assertEquals(message, outputMessage, "Received message should be equal to sent");
    }

    @Test
    @Order(2)
    public void When_UserIsNotSubscribed_And_MessageIsPublished_Then_ExpectNullOutbound() {
        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.completeHandshake(channel, "/");

        // Get session from channel id
        WebSocketSession<String, Object> session = server.getSessionByChannelId(channel.id());

        // Assert session is not null
        assertNotNull(session, "Session should not be null");

        // Publish message
        String topic = "topic-2";
        String message = "topic-2-message";
        server.publish(topic, message);

        // Assert outgoing frame is null
        assertNull(channel.readOutbound(), "Outgoing frame should be null");
    }

    @Test
    @Order(3)
    public void When_UserUnsubscribedTopic_Then_ShouldNotReceiveMessage() {
        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.completeHandshake(channel, "/");

        // Get session from channel id
        WebSocketSession<String, Object> session = server.getSessionByChannelId(channel.id());

        // Assert session is not null
        assertNotNull(session, "Session should not be null");

        // Subscribe to topic
        String topic = "topic-2";
        server.subscribe(session, topic);

        // Publish message
        String message = "topic-2-message";
        server.publish(topic, message);

        // Assert outgoing frame is null
        assertNotNull(channel.readOutbound(), "Outgoing frame should not be null");

        // Unsubscribe and publish again
        server.unsubscribe(session, topic);
        server.publish(topic, message);

        // Assert outgoing frame is null
        assertNull(channel.readOutbound(), "Outgoing frame should be null");

        // Clean up
        server.unsubscribeAllTopics();
    }

    @Test
    @Order(4)
    public void When_AllUsersUnsubscribedTopic_Then_TopicShouldBeRemoved() {
        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.completeHandshake(channel, "/");

        // Get session from channel id
        WebSocketSession<String, Object> session = server.getSessionByChannelId(channel.id());

        // Assert session is not null
        assertNotNull(session, "Session should not be null");

        // Subscribe to topic
        String topic = "topic-2";
        server.subscribe(session, topic);

        // Assert that topic was registered
        assertFalse(server.getTopics().isEmpty(), "List of topics should not be empty");

        // Unsubscribe topic
        server.unsubscribe(session, topic);

        // Assert that topic was removed
        assertTrue(server.getTopics().isEmpty(), "List of topics should be empty");
    }

    @Test
    @Order(5)
    public void When_UserIsConnectedAndDisconnects_Then_ShouldBeUnsubscribed() {
        // Construct channel and perform handshake
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        Util.completeHandshake(channel, "/");

        // Get session from channel id
        WebSocketSession<String, Object> session = server.getSessionByChannelId(channel.id());

        // Assert session is not null
        assertNotNull(session, "Session should not be null");

        // Subscribe to topic
        String topic = "topic-test";
        server.subscribe(session, topic);

        // Assert user is subscribed
        assertTrue(server.isSubscribed(session, topic));

        // Close connection
        channel.writeInbound(new CloseWebSocketFrame());

        // Assert session was unsubscribed and the empty topic was removed
        assertFalse(server.isSubscribed(session, topic), "Session should be unsubscribed");
        assertFalse(server.getTopics().contains(topic), "Topic should be removed");
    }

    @Test
    @Order(6)
    public void When_UserDisconnects_Then_OnlyEmptyTopicsShouldBeRemoved() {
        // Construct channels and perform handshakes, EmbeddedChannel instances share the same id by default
        EmbeddedChannel firstChannel = new EmbeddedChannel(DefaultChannelId.newInstance(), new WebSocketServerHandler<>(server));
        EmbeddedChannel secondChannel = new EmbeddedChannel(DefaultChannelId.newInstance(), new WebSocketServerHandler<>(server));
        Util.completeHandshake(firstChannel, "/");
        Util.completeHandshake(secondChannel, "/");

        // Get sessions from channel ids
        WebSocketSession<String, Object> firstSession = server.getSessionByChannelId(firstChannel.id());
        WebSocketSession<String, Object> secondSession = server.getSessionByChannelId(secondChannel.id());

        // Subscribe both sessions to a shared topic and the first one also to its own topic
        String sharedTopic = "topic-shared";
        String ownTopic = "topic-own";
        server.subscribe(firstSession, sharedTopic);
        server.subscribe(secondSession, sharedTopic);
        server.subscribe(firstSession, ownTopic);

        // Close first connection
        firstChannel.writeInbound(new CloseWebSocketFrame());

        // Assert first session left all topics, its own topic was removed and the shared one was kept
        assertFalse(server.isSubscribed(firstSession, sharedTopic), "First session should be unsubscribed");
        assertFalse(server.getTopics().contains(ownTopic), "Topic without subscribers should be removed");
        assertTrue(server.isSubscribed(secondSession, sharedTopic), "Second session should stay subscribed");

        // Clean up
        secondChannel.writeInbound(new CloseWebSocketFrame());
    }
}
