package pl.mbaracz.jwebsockets.message;

/**
 * Interface for encoding WebSocket messages into byte arrays.
 *
 * @param <T> the type of the WebSocket message to be encoded.
 */
public interface MessageEncoder<T> {

    /**
     * Encodes a WebSocket message into a byte array.
     *
     * @param message the WebSocket message to be encoded.
     * @return the byte array containing the encoded message.
     */
    byte[] encode(T message);

}