package pl.mbaracz.jwebsockets.handler;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import pl.mbaracz.jwebsockets.WebSocketSession;

/**
 * Interface for handling the upgrade of an HTTP connection to a WebSocket connection before the handshake.
 *
 * @param <T> the type of the WebSocket message.
 * @param <D> the type of additional data associated with the WebSocket session.
 */
public interface UpgradeHandler<T, D> {

    /**
     * Handles custom processing of the upgrade request before the WebSocket handshake.
     * If the upgrade handling fails, the provided HTTP response will be sent.
     *
     * @param request  the full HTTP request initiating the upgrade.
     * @param session  the WebSocket session associated with the upgrade.
     * @param response the HTTP response to be sent in case of upgrade failure.
     * @return {@code true} if the upgrade was handled successfully and the handshake should proceed, {@code false} otherwise.
     */
    boolean handleUpgrade(FullHttpRequest request, WebSocketSession<T, D> session, HttpResponse response);

}