package pl.mbaracz.jwebsockets.message.impl.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.mbaracz.jwebsockets.message.MessageDecoder;

import java.io.IOException;

public class JsonMessageDecoder<T> implements MessageDecoder<T> {

    private final Class<T> clazz;

    private final ObjectMapper mapper = new ObjectMapper();

    public JsonMessageDecoder(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public T decode(byte[] data) {
        try {
            return mapper.readValue(data,clazz);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to decode message from JSON", exception);
        }
    }
}
