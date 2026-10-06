package pl.mbaracz.jwebsockets;

import java.util.concurrent.CompletionStage;

/**
 * Broker for topic messages. Local session subscriptions are managed by {@link WebSocketServer}.
 * Implementations must be thread-safe and must not perform blocking I/O on the calling thread.
 * Calls to a handler for the same topic must be ordered and must not overlap.
 * Different topics may be delivered concurrently.
 * Broker lifecycle is owned by the application.
 *
 * @param <T> the topic message type
 */
public interface TopicBroker<T> {

    /**
     * Subscribes a message handler to the topic.
     * If the returned stage fails, the handler must be considered unsubscribed.
     *
     * @param topic   the topic.
     * @param handler the handler to subscribe.
     * @return a stage completed when the subscription has been established.
     */
    CompletionStage<Void> subscribe(String topic, TopicMessageHandler<T> handler);

    /**
     * Unsubscribes a message handler from the topic.
     * If the returned stage fails, the handler must remain logically subscribed.
     * After successful completion, the handler must not receive new messages for the topic.
     *
     * @param topic   the topic.
     * @param handler the handler to unsubscribe.
     * @return a stage completed when the subscription has been removed.
     */
    CompletionStage<Void> unsubscribe(String topic, TopicMessageHandler<T> handler);

    /**
     * Publishes a message to the topic.
     *
     * @param topic   the topic.
     * @param message the message to publish.
     * @return a stage completed when the broker has accepted the message.
     */
    CompletionStage<Void> publish(String topic, T message);
}
