package pl.mbaracz.jwebsockets.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class WebSocketServerMetricsTest {

    private MeterRegistry registry;
    private WebSocketServerMetrics<String, Object> metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new WebSocketServerMetrics<>(registry, Tags.of("server", "chat"));
    }

    @Test
    void shouldCountSessionsWhenSessionsAreOpenedAndClosed() {
        metrics.sessionOpened(null);
        metrics.sessionOpened(null);
        metrics.sessionClosed(null, 1000, "bye");

        assertSoftly(softly -> {
            softly.assertThat(registry.get("jwebsockets.sessions.active").gauge().value()).as("Active sessions").isEqualTo(1);
            softly.assertThat(registry.get("jwebsockets.sessions.opened").counter().count()).as("Opened sessions").isEqualTo(2);
            softly.assertThat(registry.get("jwebsockets.sessions.closed").counter().count()).as("Closed sessions").isEqualTo(1);
        });
    }

    @Test
    void shouldCountMessagesAndErrorsWhenTheyAreReported() {
        metrics.messageReceived(null);
        metrics.messageSent(null);
        metrics.messageSent(null);
        metrics.exception(null, new IllegalStateException());

        assertSoftly(softly -> {
            softly.assertThat(registry.get("jwebsockets.messages.received").counter().count()).as("Received messages").isEqualTo(1);
            softly.assertThat(registry.get("jwebsockets.messages.sent").counter().count()).as("Sent messages").isEqualTo(2);
            softly.assertThat(registry.get("jwebsockets.errors").counter().count()).as("Errors").isEqualTo(1);
        });
    }

    @Test
    void shouldTagEveryMetricWhenTagsAreGiven() {
        assertThat(registry.find("jwebsockets.sessions.active").tag("server", "chat").gauge()).as("Gauge should be tagged").isNotNull();
        assertThat(registry.find("jwebsockets.errors").tag("server", "chat").counter()).as("Counter should be tagged").isNotNull();
    }
}
