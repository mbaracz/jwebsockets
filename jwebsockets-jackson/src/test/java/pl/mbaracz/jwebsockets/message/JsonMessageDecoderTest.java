package pl.mbaracz.jwebsockets.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.json.JsonMessageDecoder;

import java.io.IOException;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private JsonMessageDecoder<TestMessage> decoder;

    @BeforeEach
    public void setUp() {
        decoder = new JsonMessageDecoder<>(TestMessage.class);
    }

    @Test
    public void shouldReturnOriginalMessageWhenValidJsonIsDecoded() throws IOException {
        // Given
        TestMessage originalMessage = new TestMessage("hello", 123);
        ObjectMapper mapper = new ObjectMapper();

        // When
        byte[] encoded = mapper.writeValueAsBytes(originalMessage);
        TestMessage decodedMessage = decoder.decode(encoded);

        // Then
        assertThat(decodedMessage).isEqualTo(originalMessage);
    }

    @Test
    public void shouldThrowRuntimeExceptionWhenEmptyJsonIsDecoded() {
        // Given
        String emptyJson = "";

        // When / Then
        assertThatThrownBy(() -> decoder.decode(emptyJson.getBytes())).isInstanceOf(RuntimeException.class);
    }
}
