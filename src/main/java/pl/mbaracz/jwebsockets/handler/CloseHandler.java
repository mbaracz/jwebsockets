package pl.mbaracz.jwebsockets.handler;

import pl.mbaracz.jwebsockets.WebSocketSession;

/**
 * Interface for handling WebSocket connection closure events.
 *
 * @param <T> the type of the WebSocket message.
 * @param <D> the type of additional data associated with the WebSocket session.
 */
public interface CloseHandler<T, D> {

    /**
     * Handles the closure of a WebSocket session.
     *
     * @param session the WebSocket session that is being closed.
     * @param reason  the reason for the WebSocket connection closure.
     * @param code    the status code indicating the closure reason.
     */
    void handleClose(WebSocketSession<T, D> session, String reason, int code);

}
