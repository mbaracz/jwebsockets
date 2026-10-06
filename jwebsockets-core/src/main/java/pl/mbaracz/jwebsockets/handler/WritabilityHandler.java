package pl.mbaracz.jwebsockets.handler;

import pl.mbaracz.jwebsockets.WebSocketSession;

/**
 * Interface for handling changes of WebSocket session writability.
 *
 * @param <T> the type of the WebSocket message.
 * @param <D> the type of additional data associated with the WebSocket session.
 */
@FunctionalInterface
public interface WritabilityHandler<T, D> {

    /**
     * Handles a change of the session's writability.
     *
     * @param session  the WebSocket session whose writability changed.
     * @param writable whether the session is writable now.
     */
    void handleWritabilityChanged(WebSocketSession<T, D> session, boolean writable);

}
