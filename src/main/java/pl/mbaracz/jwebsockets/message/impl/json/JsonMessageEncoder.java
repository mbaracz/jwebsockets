package pl.mbaracz.jwebsockets.message.impl.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.mbaracz.jwebsockets.message.MessageEncoder;

public class JsonMessageEncoder<T> implements MessageEncoder<T> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public byte[] encode(T message) {
        try {
            return mapper.writeValueAsBytes(message);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("Failed to encode message to JSON", exception);
        }
    }
}
