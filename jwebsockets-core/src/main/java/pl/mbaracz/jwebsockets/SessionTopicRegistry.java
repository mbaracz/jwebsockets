package pl.mbaracz.jwebsockets;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps the local topic subscriptions of WebSocket sessions.
 */
final class SessionTopicRegistry<T, D> {

    private final Map<String, Set<WebSocketSession<T, D>>> topics = new HashMap<>();

    synchronized void subscribe(WebSocketSession<T, D> session, String topic) {
        Objects.requireNonNull(session, "WebSocket session must not be null!");
        Objects.requireNonNull(topic, "Topic must not be null!");
        topics.computeIfAbsent(topic, _ -> new HashSet<>()).add(session);
    }

    synchronized void unsubscribe(WebSocketSession<T, D> session, String topic) {
        Objects.requireNonNull(topic, "Topic must not be null!");
        Set<WebSocketSession<T, D>> subscribers = topics.get(topic);

        if (subscribers != null) {
            subscribers.remove(session);

            if (subscribers.isEmpty()) {
                topics.remove(topic);
            }
        }
    }

    synchronized void unsubscribeAll(WebSocketSession<T, D> session) {
        topics.entrySet().removeIf(entry -> {
            entry.getValue().remove(session);
            return entry.getValue().isEmpty();
        });
    }

    synchronized void clear() {
        topics.clear();
    }

    synchronized boolean isSubscribed(WebSocketSession<T, D> session, String topic) {
        Set<WebSocketSession<T, D>> subscribers = topics.get(topic);
        return subscribers != null && subscribers.contains(session);
    }

    synchronized Set<String> getTopics() {
        return Set.copyOf(topics.keySet());
    }

    synchronized Collection<WebSocketSession<T, D>> getSubscribers(String topic) {
        Set<WebSocketSession<T, D>> subscribers = topics.get(topic);
        return subscribers == null ? List.of() : List.copyOf(subscribers);
    }
}
