package pl.mbaracz.jwebsockets.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.json.JsonMessageEncoder;

import java.io.IOException;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JsonMessageEncoderTest {

    private static class TestMessage {
        public String text;
        public int number;

        public TestMessage() {}

        public TestMessage(String text, int number) {
            this.text = text;
            this.number = number;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestMessage that = (TestMessage) o;
            if (number != that.number) return false;
            return Objects.equals(text, that.text);
        }
    }

    private static class CyclicTestMessage {
        public String text;
        public CyclicTestMessage selfReference;

        public CyclicTestMessage(String text) {
            this.text = text;
            this.selfReference = this;
        }
    }

    private JsonMessageEncoder<Object> encoder;

    @BeforeEach
    void setUp() {
        encoder = new JsonMessageEncoder<>();
    }

    @Test
    void shouldEncodeMessageDecodableToOriginalMessageWhenMessageIsEncoded() throws IOException {
        // Given
        TestMessage message = new TestMessage("hello", 123);
        ObjectMapper mapper = new ObjectMapper();

        // When
        byte[] encoded = encoder.encode(message);
        TestMessage decodedMessage = mapper.readValue(encoded, TestMessage.class);

        // Then
        assertThat(decodedMessage).isEqualTo(message);
    }

    @Test
    void shouldThrowRuntimeExceptionWhenEncodingFails() {
        // Given
        CyclicTestMessage message = new CyclicTestMessage("hello");

        // When / Then
        assertThatThrownBy(() -> encoder.encode(message))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to encode message to JSON");
    }
}
