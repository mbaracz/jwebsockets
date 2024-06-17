package pl.mbaracz.jwebsockets.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.json.JsonMessageEncoder;

import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    public void When_MessageIsEncoded_Then_EncodedMessageCanBeDecodedToOriginalMessage() throws IOException {
        // Given
        TestMessage message = new TestMessage("hello", 123);
        JsonMessageEncoder<TestMessage> encoder = new JsonMessageEncoder<>();
        ObjectMapper mapper = new ObjectMapper();

        // When
        byte[] encoded = encoder.encode(message);
        TestMessage decodedMessage = mapper.readValue(encoded, TestMessage.class);

        // Then
        assertEquals(message, decodedMessage);
    }

    @Test
    public void When_EncodingFails_Then_RuntimeExceptionIsThrown() {
        // Given
        CyclicTestMessage message = new CyclicTestMessage("hello");
        JsonMessageEncoder<CyclicTestMessage> encoder = new JsonMessageEncoder<>();

        // When / Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> encoder.encode(message));
        assertTrue(exception.getMessage().contains("Failed to encode message to JSON"));
    }
}
