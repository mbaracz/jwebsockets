package pl.mbaracz.jwebsockets;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Topic broker keeping the subscriptions in the memory of this JVM.
 *
 * @param <T> the type of WebSocket messages.
 * @param <D> the type of the session context.
 */
final class InMemoryTopicBroker<T, D> implements TopicBroker<T, D> {

    private final Map<String, Set<WebSocketSession<T, D>>> topics = new ConcurrentHashMap<>();

    @Override
    public void subscribe(String topic, WebSocketSession<T, D> session) {
        // Add the subscriber inside compute so the topic cannot be removed
        // concurrently between retrieving the set and adding the session.
        topics.compute(topic, (_, subscribers) -> {
            if (subscribers == null) {
                subscribers = ConcurrentHashMap.newKeySet();
            }
            subscribers.add(session);
            return subscribers;
        });
    }

    @Override
    public boolean isSubscribed(String topic, WebSocketSession<T, D> session) {
        Set<WebSocketSession<T, D>> subscribers = topics.get(topic);
        return subscribers != null && subscribers.contains(session);
    }

    @Override
    public void unsubscribe(String topic, WebSocketSession<T, D> session) {
        topics.computeIfPresent(topic, (_, subscribers) -> {
            subscribers.remove(session);
            return subscribers.isEmpty() ? null : subscribers;
        });
    }

    @Override
    public void unsubscribeAll(WebSocketSession<T, D> session) {
        // unsubscribe() also removes topics that are left without subscribers
        topics.keySet().forEach(topic -> unsubscribe(topic, session));
    }

    @Override
    public void clear() {
        topics.clear();
    }

    @Override
    public void publish(String topic, T message) {
        topics.getOrDefault(topic, Collections.emptySet())
            .forEach(session -> session.sendMessage(message));
    }

    @Override
    public Set<String> getTopics() {
        return Set.copyOf(topics.keySet());
    }
}
