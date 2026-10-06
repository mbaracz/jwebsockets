package pl.mbaracz.jwebsockets;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Topic broker transporting messages within this JVM.
 *
 * @param <T> the type of topic messages.
 */
final class InMemoryTopicBroker<T> implements TopicBroker<T> {

    private final Map<String, Set<TopicMessageHandler<T>>> topics = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Void> subscribe(String topic, TopicMessageHandler<T> handler) {
        Objects.requireNonNull(topic, "Topic must not be null!");
        Objects.requireNonNull(handler, "Topic message handler must not be null!");
        topics.compute(topic, (_, handlers) -> {
            if (handlers == null) {
                handlers = ConcurrentHashMap.newKeySet();
            }
            handlers.add(handler);
            return handlers;
        });
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> unsubscribe(String topic, TopicMessageHandler<T> handler) {
        Objects.requireNonNull(topic, "Topic must not be null!");
        Objects.requireNonNull(handler, "Topic message handler must not be null!");
        topics.computeIfPresent(topic, (_, handlers) -> {
            handlers.remove(handler);
            return handlers.isEmpty() ? null : handlers;
        });
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> publish(String topic, T message) {
        Objects.requireNonNull(topic, "Topic must not be null!");

        try {
            topics.getOrDefault(topic, Collections.emptySet())
                .forEach(handler -> handler.onMessage(topic, message));
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }
}
