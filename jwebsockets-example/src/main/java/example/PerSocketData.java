package example;

/**
 * Session context populated during the WebSocket upgrade.
 */
public record PerSocketData(long id, String name) {
}
