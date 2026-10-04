package pl.mbaracz.jwebsockets.micrometer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import pl.mbaracz.jwebsockets.WebSocketServerObserver;
import pl.mbaracz.jwebsockets.WebSocketSession;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records the sessions, messages and errors of a WebSocket server in a Micrometer registry.
 *
 * @param <T> the type of the WebSocket message.
 * @param <D> the type of additional data associated with the WebSocket session.
 */
public class WebSocketServerMetrics<T, D> implements WebSocketServerObserver<T, D> {

    private final AtomicInteger activeSessions = new AtomicInteger();
    private final Counter sessionsOpened;
    private final Counter sessionsClosed;
    private final Counter messagesReceived;
    private final Counter messagesSent;
    private final Counter errors;

    /**
     * Registers the server metrics in the given registry.
     *
     * @param registry the registry to record the metrics in.
     */
    public WebSocketServerMetrics(MeterRegistry registry) {
        this(registry, Tags.empty());
    }

    /**
     * Registers the server metrics with the given tags in the given registry.
     *
     * @param registry the registry to record the metrics in.
     * @param tags     the tags added to every metric, e.g. to tell several servers apart.
     */
    public WebSocketServerMetrics(MeterRegistry registry, Iterable<Tag> tags) {
        Gauge.builder("jwebsockets.sessions.active", activeSessions, AtomicInteger::get)
            .description("The number of open sessions")
            .tags(tags)
            .register(registry);

        this.sessionsOpened = counter(registry, tags, "jwebsockets.sessions.opened", "The number of opened sessions");
        this.sessionsClosed = counter(registry, tags, "jwebsockets.sessions.closed", "The number of closed sessions");
        this.messagesReceived = counter(registry, tags, "jwebsockets.messages.received", "The number of received messages");
        this.messagesSent = counter(registry, tags, "jwebsockets.messages.sent", "The number of messages successfully written to connections");
        this.errors = counter(registry, tags, "jwebsockets.errors", "The number of exceptions caught on connections");
    }

    private static Counter counter(MeterRegistry registry, Iterable<Tag> tags, String name, String description) {
        return Counter.builder(name)
            .description(description)
            .tags(tags)
            .register(registry);
    }

    @Override
    public void sessionOpened(WebSocketSession<T, D> session) {
        activeSessions.incrementAndGet();
        sessionsOpened.increment();
    }

    @Override
    public void sessionClosed(WebSocketSession<T, D> session, int code, String reason) {
        activeSessions.decrementAndGet();
        sessionsClosed.increment();
    }

    @Override
    public void messageReceived(WebSocketSession<T, D> session) {
        messagesReceived.increment();
    }

    @Override
    public void messageSent(WebSocketSession<T, D> session) {
        messagesSent.increment();
    }

    @Override
    public void exception(WebSocketSession<T, D> session, Throwable exception) {
        errors.increment();
    }
}
