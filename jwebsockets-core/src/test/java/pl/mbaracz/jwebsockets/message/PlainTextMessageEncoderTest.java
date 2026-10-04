package pl.mbaracz.jwebsockets.message;

import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import static org.assertj.core.api.Assertions.assertThat;

public class PlainTextMessageEncoderTest {

    @Test
    public void shouldReturnOriginalMessageWhenEncodedMessageIsDecoded() {
        // Given
        String message = "hello";

        // When
        byte[] encoded = PlainTextMessageEncoder.INSTANCE.encode(message);

        // Then
        assertThat(PlainTextMessageDecoder.INSTANCE.decode(encoded)).isEqualTo(message);
    }
}
