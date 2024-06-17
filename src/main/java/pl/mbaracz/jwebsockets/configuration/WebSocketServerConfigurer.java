package pl.mbaracz.jwebsockets.configuration;

/**
 * Interface for configuring WebSocket server settings.
 *
 * @param <T> the type of messages that will be handled by the WebSocket server.
 */
public interface WebSocketServerConfigurer<T> {

    /**
     * Configures the WebSocket server settings.
     *
     * @param configurer the WebSocket server configuration to be customized.
     */
    void configure(WebSocketServerConfiguration<T> configurer);

}
