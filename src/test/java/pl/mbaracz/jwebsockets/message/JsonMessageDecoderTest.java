package pl.mbaracz.jwebsockets.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.json.JsonMessageDecoder;

import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class JsonMessageDecoderTest {

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

    @Test
    public void When_ValidJsonIsDecoded_Then_OriginalMessageIsReturned() {
        // Given
        TestMessage originalMessage = new TestMessage("hello", 123);
        JsonMessageDecoder<TestMessage> decoder = new JsonMessageDecoder<>(TestMessage.class);
        ObjectMapper mapper = new ObjectMapper();

        try {
            // When
            byte[] encoded = mapper.writeValueAsBytes(originalMessage);
            TestMessage decodedMessage = decoder.decode(encoded);

            // Then
            assertEquals(originalMessage, decodedMessage);
        } catch (IOException e) {
            fail("Exception should not be thrown");
        }
    }

    @Test
    public void When_EmptyJsonIsDecoded_Then_RuntimeExceptionIsThrown() {
        // Given
        String emptyJson = "";
        JsonMessageDecoder<TestMessage> decoder = new JsonMessageDecoder<>(TestMessage.class);

        // When / Then
        assertThrows(RuntimeException.class, () -> decoder.decode(emptyJson.getBytes()));
    }
}
