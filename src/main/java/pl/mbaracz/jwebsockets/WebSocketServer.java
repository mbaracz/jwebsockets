package pl.mbaracz.jwebsockets;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelId;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.mbaracz.jwebsockets.configuration.WebSocketServerConfiguration;
import pl.mbaracz.jwebsockets.configuration.WebSocketServerConfigurer;
import pl.mbaracz.jwebsockets.handler.CloseHandler;
import pl.mbaracz.jwebsockets.handler.MessageHandler;
import pl.mbaracz.jwebsockets.handler.OpenHandler;
import pl.mbaracz.jwebsockets.handler.UpgradeHandler;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocketServer represents a WebSocket server that listens for incoming WebSocket connections.
 * It allows configuring message handlers, open handlers, close handlers, and upgrade handlers.
 *
 * @param <T> Type of messages to be handled by the server
 * @param <D> Type of additional data associated with the session
 */
public class WebSocketServer<T, D> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketServer.class);

    private final String path;
    private Thread serverThread;
    private OpenHandler<T, D> openHandler;
    private UpgradeHandler<T, D> upgradeHandler;
    private CloseHandler<T, D> closeHandler;
    private MessageHandler<T, D> messageHandler;
    private CompletableFuture<Void> completableFuture;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<ChannelId, WebSocketSession<T, D>> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession<T, D>>> topics = new HashMap<>();
    private final WebSocketServerConfiguration<T> configuration = new WebSocketServerConfiguration<>();

    /**
     * Default constructor initializing the WebSocket server with the root path.
     */
    public WebSocketServer() {
        this.path = "/";
    }

    /**
     * Constructor initializing the WebSocket server with a specified path.
     *
     * @param path The path for the WebSocket server
     */
    public WebSocketServer(String path) {
        this.path = path;
    }

    /**
     * Configures the WebSocket server using the provided configurer.
     *
     * @param configurer Configurer for WebSocket server
     * @return The WebSocket server instance for method chaining
     */
    public WebSocketServer<T, D> configure(WebSocketServerConfigurer<T> configurer) {
        configurer.configure(configuration);
        return this;
    }

    /**
     * Sets the message handler for processing incoming messages.
     *
     * @param handler Message handler to be set
     * @return The WebSocket server instance for method chaining
     */
    public WebSocketServer<T, D> onMessage(MessageHandler<T, D> handler) {
        this.messageHandler = handler;
        return this;
    }

    /**
     * Sets the close handler for handling WebSocket session closure.
     *
     * @param handler Close handler to be set
     * @return The WebSocket server instance for method chaining
     */
    public WebSocketServer<T, D> onClose(CloseHandler<T, D> handler) {
        this.closeHandler = handler;
        return this;
    }

    /**
     * Sets the open handler for handling new WebSocket connections.
     *
     * @param handler Open handler to be set
     * @return The WebSocket server instance for method chaining
     */
    public WebSocketServer<T, D> onOpen(OpenHandler<T, D> handler) {
        this.openHandler = handler;
        return this;
    }

    /**
     * Sets the upgrade handler for handling WebSocket protocol upgrade requests.
     *
     * @param handler Upgrade handler to be set
     * @return The WebSocket server instance for method chaining
     */
    public WebSocketServer<T, D> onUpgrade(UpgradeHandler<T, D> handler) {
        this.upgradeHandler = handler;
        return this;
    }

    /**
     * Subscribes a WebSocket session to a given topic.
     *
     * @param session The WebSocket session to subscribe.
     * @param topic   The topic to subscribe the session to.
     */
    public void subscribe(WebSocketSession<T, D> session, String topic) {
        topics.computeIfAbsent(topic, k -> new HashSet<>()).add(session);
    }

    /**
     * Checks if a WebSocket session is subscribed to a given topic.
     *
     * @param session The WebSocket session to check.
     * @param topic   The topic to check the subscription for.
     * @return true if the session is subscribed to the topic, false otherwise.
     */
    public boolean isSubscribed(WebSocketSession<T, D> session, String topic) {
        Set<WebSocketSession<T, D>> subscribers = topics.get(topic);
        return subscribers != null && subscribers.contains(session);
    }

    /**
     * Unsubscribes a WebSocket session from a given topic.
     *
     * @param session The WebSocket session to unsubscribe.
     * @param topic   The topic to unsubscribe the session from.
     */
    public void unsubscribe(WebSocketSession<T, D> session, String topic) {
        Set<WebSocketSession<T, D>> subscribers = topics.get(topic);

        if (subscribers == null) {
            return;
        }

        subscribers.remove(session);

        if (subscribers.isEmpty()) {
            topics.remove(topic);
        }
    }

    /**
     * Unsubscribes all WebSocket sessions from all topics.
     */
    public void unsubscribeAllTopics() {
        topics.clear();
    }

    /**
     * Publishes a message to all WebSocket sessions subscribed to a given topic.
     *
     * @param topic   The topic to which the message will be published.
     * @param message The message to be published.
     */
    public void publish(String topic, T message) {
        topics.getOrDefault(topic, Collections.emptySet()).forEach(session -> session.sendMessage(message));
    }

    /**
     * Retrieves all the topics to which WebSocket sessions are subscribed.
     *
     * @return A set of topics
     */
    public Set<String> getTopics() {
        return topics.keySet();
    }

    /**
     * Starts the WebSocket server and listens for incoming connections on the specified port.
     *
     * @param port Port number to listen on
     * @return The WebSocket server instance for method chaining
     * @throws IllegalStateException If the server is already running on the specified port or message encoder/decoder was not provided
     */
    public WebSocketServer<T, D> listen(int port) throws IllegalStateException {
        if (running.get()) {
            throw new IllegalStateException("WebSocket server is already running on port " + port + "!");
        }
        if (configuration.getMessageDecoder() == null) {
            throw new IllegalStateException("Message decoder is not provided, cannot start the server!");
        }
        if (configuration.getMessageEncoder() == null) {
            throw new IllegalStateException("Message encoder is not provided, cannot start the server!");
        }

        completableFuture = new CompletableFuture<>();

        serverThread = new Thread(() -> {
            EventLoopGroup bossGroup = new NioEventLoopGroup(1);
            EventLoopGroup workerGroup = new NioEventLoopGroup();

            try {
                ServerBootstrap bootstrap = new ServerBootstrap()
                        .group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new WebSocketServerChannelInitializer<>(this));

                bootstrap.bind(port)
                        .addListener(future -> {
                            if (future.isSuccess()) {
                                logger.info("Started WebSocket server at ws://localhost:" + port);
                                running.set(true);
                                completableFuture.complete(null);
                            }
                        })
                        .sync()
                        .channel()
                        .closeFuture()
                        .sync();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
                running.set(false);
                completableFuture.complete(null);
            }
        });

        serverThread.start();

        completableFuture.join();
        return this;
    }

    /**
     * Stops the WebSocket server gracefully.
     * This method shuts down the event loop groups and sets the running flag to false.
     */
    public void stop() {
        if (!running.get()) {
            throw new IllegalStateException("Server is already stopped!");
        }
        logger.info("Stopping WebSocket server...");

        completableFuture = new CompletableFuture<>();
        serverThread.interrupt();
        completableFuture.join();

        logger.info("Server stopped!");
    }

    /**
     * Broadcasts a message to all connected WebSocket sessions.
     *
     * @param message The message to be broadcast
     */
    public synchronized void broadcast(T message) {
        if (!running.get()) {
            throw new IllegalStateException("Server is not running, cannot broadcast!");
        }
        sessions.values().forEach(session -> session.sendMessage(message));
    }

    /**
     * Returns an unmodifiable collection of all currently connected WebSocket sessions.
     *
     * @return A collection of connected WebSocket sessions
     */
    public synchronized Collection<WebSocketSession<T, D>> getConnectedSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    /**
     * Checks if the WebSocket server is currently running.
     *
     * @return True if the server is running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Retrieves a WebSocket session by its channel ID.
     *
     * @param id The channel ID of the session to retrieve
     * @return The WebSocket session associated with the given channel ID, or null if no session exists for the ID
     */
    synchronized WebSocketSession<T, D> getSessionByChannelId(ChannelId id) {
        return sessions.get(id);
    }

    /**
     * Removes a WebSocket session associated with the given channel ID.
     *
     * @param id The channel ID of the session to remove
     */
    synchronized void removeSession(ChannelId id) {
        sessions.remove(id);
    }

    /**
     * Adds a WebSocket session associated with the given channel ID.
     *
     * @param id      The channel ID of the session to add
     * @param session The WebSocket session to add
     */
    synchronized void addSession(ChannelId id, WebSocketSession<T, D> session) {
        sessions.put(id, session);
    }

    String getPath() {
        return path;
    }

    WebSocketServerConfiguration<T> getConfiguration() {
        return configuration;
    }

    OpenHandler<T, D> getOpenHandler() {
        return openHandler;
    }

    UpgradeHandler<T, D> getUpgradeHandler() {
        return upgradeHandler;
    }

    MessageHandler<T, D> getMessageHandler() {
        return messageHandler;
    }

    CloseHandler<T, D> getCloseHandler() {
        return closeHandler;
    }
}
