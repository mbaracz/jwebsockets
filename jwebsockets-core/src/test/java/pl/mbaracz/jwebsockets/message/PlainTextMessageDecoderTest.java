package pl.mbaracz.jwebsockets.message;

import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PlainTextMessageDecoderTest {

    @Test
    public void When_EncodedMessageIsDecoded_Then_OriginalMessageIsReturned() {
        // Given
        String message = "hello";

        // When
        byte[] encoded = PlainTextMessageEncoder.INSTANCE.encode(message);

        // Then
        assertEquals(PlainTextMessageDecoder.INSTANCE.decode(encoded), message);
    }
}
