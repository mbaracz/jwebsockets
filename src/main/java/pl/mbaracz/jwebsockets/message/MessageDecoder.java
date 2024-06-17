package pl.mbaracz.jwebsockets.message;

/**
 * Interface for decoding WebSocket messages from byte arrays.
 *
 * @param <T> the type of the decoded WebSocket message.
 */
public interface MessageDecoder<T> {

    /**
     * Decodes a WebSocket message from a byte array.
     *
     * @param data the byte array containing the encoded message.
     * @return the decoded WebSocket message.
     */
    T decode(byte[] data);

}