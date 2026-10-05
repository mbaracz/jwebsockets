package pl.mbaracz.jwebsockets;

import java.util.Set;

/**
 * Keeps the topic subscriptions of WebSocket sessions and publishes messages to the subscribers of a topic.
 * Set it with {@link WebSocketServer#topicBroker(TopicBroker)}, an in-memory broker is used by default.
 * Its methods are called concurrently from event loops and application threads, so it must be thread-safe.
 *
 * @param <T> the type of WebSocket messages.
 * @param <D> the type of the session context.
 */
public interface TopicBroker<T, D> {

    /**
     * Subscribes the session to the topic.
     *
     * @param topic   the topic.
     * @param session the session to subscribe.
     */
    void subscribe(String topic, WebSocketSession<T, D> session);

    /**
     * Checks whether the session is subscribed to the topic.
     *
     * @param topic   the topic.
     * @param session the session to check.
     * @return true if the session is subscribed to the topic, false otherwise.
     */
    boolean isSubscribed(String topic, WebSocketSession<T, D> session);

    /**
     * Unsubscribes the session from the topic, which is removed once it has no subscribers.
     *
     * @param topic   the topic.
     * @param session the session to unsubscribe.
     */
    void unsubscribe(String topic, WebSocketSession<T, D> session);

    /**
     * Unsubscribes the session from all of its topics.
     *
     * @param session the session to unsubscribe.
     */
    void unsubscribeAll(WebSocketSession<T, D> session);

    /**
     * Removes the subscriptions of all sessions.
     */
    void clear();

    /**
     * Sends the message to all sessions subscribed to the topic.
     *
     * @param topic   the topic.
     * @param message the message to publish.
     */
    void publish(String topic, T message);

    /**
     * Returns an unmodifiable snapshot of the topics that have subscribers.
     *
     * @return the topics.
     */
    Set<String> getTopics();
}
