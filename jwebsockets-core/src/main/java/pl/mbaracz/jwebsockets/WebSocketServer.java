package pl.mbaracz.jwebsockets;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelId;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import io.netty.util.concurrent.EventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.mbaracz.jwebsockets.configuration.WebSocketServerConfiguration;
import pl.mbaracz.jwebsockets.configuration.WebSocketServerConfigurer;
import pl.mbaracz.jwebsockets.handler.CloseHandler;
import pl.mbaracz.jwebsockets.handler.ErrorHandler;
import pl.mbaracz.jwebsockets.handler.MessageHandler;
import pl.mbaracz.jwebsockets.handler.OpenHandler;
import pl.mbaracz.jwebsockets.handler.UpgradeHandler;
import pl.mbaracz.jwebsockets.handler.WritabilityHandler;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * WebSocketServer represents a WebSocket server that listens for incoming WebSocket connections.
 * It allows configuring message, lifecycle, error, and upgrade handlers.
 *
 * @param <T> Type of messages to be handled by the server
 * @param <D> Type of additional data associated with the session
 */
public class WebSocketServer<T, D> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketServer.class);

    // Upper bound for closing client connections and for event loop termination in stop()
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final String path;
    private OpenHandler<T, D> openHandler;
    private UpgradeHandler<D> upgradeHandler;
    private CloseHandler<T, D> closeHandler;
    private MessageHandler<T, D> messageHandler;
    private ErrorHandler<T, D> errorHandler;
    private WritabilityHandler<T, D> writabilityHandler;
    private WebSocketServerObserver<T, D> observer;

    // Guards listen() and stop() without blocking session registry operations,
    // so stopping the server never waits on a lock held by session callbacks.
    private final Object lifecycleLock = new Object();
    private volatile State state = State.STOPPED;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private volatile Channel serverChannel;

    private volatile TopicBroker<T, D> topicBroker = new InMemoryTopicBroker<>();
    private volatile WebSocketServerConfiguration<T> configuration = WebSocketServerConfiguration.<T>builder().build();
    private final SessionRegistry<T, D> sessionRegistry = new SessionRegistry<>();
    private final Object sessionTopicLock = new Object();

    private enum State {
        STOPPED,
        RUNNING,
        STOPPING
    }

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
     * The configurer gets a builder with the current settings, and the server keeps the configuration built from it.
     *
     * @param configurer Configurer for WebSocket server
     * @return The WebSocket server instance for method chaining
     * @throws IllegalStateException If the server is running or stopping
     */
    public WebSocketServer<T, D> configure(WebSocketServerConfigurer<T> configurer) {
        synchronized (lifecycleLock) {
            ensureConfigurable();

            WebSocketServerConfiguration.Builder<T> builder = configuration.toBuilder();
            configurer.configure(builder);
            configuration = builder.build();
        }
        return this;
    }

    /**
     * Sets the message handler for processing incoming messages.
     *
     * @param handler Message handler to be set
     * @return The WebSocket server instance for method chaining
     * @throws IllegalStateException If the server is running or stopping
     */
    public WebSocketServer<T, D> onMessage(MessageHandler<T, D> handler) {
        synchronized (lifecycleLock) {
            ensureConfigurable();
            this.messageHandler = handler;
        }
        return this;
    }

    /**
     * Sets the application handler for errors reported by WebSocket connections.
     * The handler is invoked on the connection's event loop and should not block.
     * Its exceptions are logged and do not affect the connection or its configured close policy.
     *
     * @param handler Error handler to be set
     * @return The WebSocket server instance for method chaining
     * @throws IllegalStateException If the server is running or stopping
     */
    public WebSocketServer<T, D> onError(ErrorHandler<T, D> handler) {
        synchronized (lifecycleLock) {
            ensureConfigurable();
            this.errorHandler = handler;
        }
        return this;
    }

    /**
     * Sets the close handler for handling WebSocket session closure.
     *
     * @param handler Close handler to be set
     * @return The WebSocket server instance for method chaining
     * @throws IllegalStateException If the server is running or stopping
     */
    public WebSocketServer<T, D> onClose(CloseHandler<T, D> handler) {
        synchronized (lifecycleLock) {
            ensureConfigurable();
            this.closeHandler = handler;
        }
        return this;
    }

    /**
     * Sets the open handler for handling new WebSocket connections.
     *
     * @param handler Open handler to be set
     * @return The WebSocket server instance for method chaining
     * @throws IllegalStateException If the server is running or stopping
     */
    public WebSocketServer<T, D> onOpen(OpenHandler<T, D> handler) {
        synchronized (lifecycleLock) {
            ensureConfigurable();
            this.openHandler = handler;
        }
        return this;
    }

    /**
     * Sets the upgrade handler for handling WebSocket protocol upgrade requests.
     *
     * @param handler Upgrade handler to be set
     * @return The WebSocket server instance for method chaining
     * @throws IllegalStateException If the server is running or stopping
     */
    public WebSocketServer<T, D> onUpgrade(UpgradeHandler<D> handler) {
        synchronized (lifecycleLock) {
            ensureConfigurable();
            this.upgradeHandler = handler;
        }
        return this;
    }

    /**
     * Sets the handler for changes of WebSocket session writability.
     * Without a callback executor the handler runs on the connection's event loop and should not block.
     *
     * @param handler Writability handler to be set
     * @return The WebSocket server instance for method chaining
     * @throws IllegalStateException If the server is running or stopping
     */
    public WebSocketServer<T, D> onWritabilityChanged(WritabilityHandler<T, D> handler) {
        synchronized (lifecycleLock) {
            ensureConfigurable();
            this.writabilityHandler = handler;
        }
        return this;
    }

    /**
     * Sets the topic broker keeping the subscriptions and publishing messages, an in-memory broker by default.
     *
     * @param topicBroker Topic broker to be used
     * @return The WebSocket server instance for method chaining
     * @throws IllegalStateException If the server is running or stopping
     */
    public WebSocketServer<T, D> topicBroker(TopicBroker<T, D> topicBroker) {
        Objects.requireNonNull(topicBroker, "Topic broker must not be null!");

        synchronized (lifecycleLock) {
            ensureConfigurable();
            this.topicBroker = topicBroker;
        }
        return this;
    }

    /**
     * Sets the observer notified about sessions, messages and exceptions, e.g. to record metrics.
     * The observer is invoked on the connection's event loop and should not block.
     *
     * @param observer Observer to be set
     * @return The WebSocket server instance for method chaining
     * @throws IllegalStateException If the server is running or stopping
     */
    public WebSocketServer<T, D> observer(WebSocketServerObserver<T, D> observer) {
        synchronized (lifecycleLock) {
            ensureConfigurable();
            this.observer = observer;
        }
        return this;
    }

    private void ensureConfigurable() {
        if (state != State.STOPPED) {
            throw new IllegalStateException("Server cannot be reconfigured while running or stopping!");
        }
    }

    /**
     * Subscribes a WebSocket session to a given topic.
     *
     * @param session The WebSocket session to subscribe.
     * @param topic   The topic to subscribe the session to.
     * @throws IllegalStateException If the session is not connected to this server
     */
    public void subscribe(WebSocketSession<T, D> session, String topic) {
        synchronized (sessionTopicLock) {
            if (!sessionRegistry.contains(session)) {
                throw new IllegalStateException("WebSocket session is not connected");
            }

            topicBroker.subscribe(topic, session);
        }
    }

    /**
     * Checks if a WebSocket session is subscribed to a given topic.
     *
     * @param session The WebSocket session to check.
     * @param topic   The topic to check the subscription for.
     * @return true if the session is subscribed to the topic, false otherwise.
     */
    public boolean isSubscribed(WebSocketSession<T, D> session, String topic) {
        return topicBroker.isSubscribed(topic, session);
    }

    /**
     * Unsubscribes a WebSocket session from a given topic.
     *
     * @param session The WebSocket session to unsubscribe.
     * @param topic   The topic to unsubscribe the session from.
     */
    public void unsubscribe(WebSocketSession<T, D> session, String topic) {
        topicBroker.unsubscribe(topic, session);
    }

    /**
     * Unsubscribes all WebSocket sessions from all topics.
     */
    public void unsubscribeAllTopics() {
        topicBroker.clear();
    }

    /**
     * Publishes a message to all WebSocket sessions subscribed to a given topic.
     *
     * @param topic   The topic to which the message will be published.
     * @param message The message to be published.
     */
    public void publish(String topic, T message) {
        topicBroker.publish(topic, message);
    }

    /**
     * Retrieves a snapshot of all the topics to which WebSocket sessions are subscribed.
     * The returned set is unmodifiable and does not change when sessions subscribe or unsubscribe later.
     *
     * @return A set of topics
     */
    public Set<String> getTopics() {
        return topicBroker.getTopics();
    }

    /**
     * Starts the WebSocket server and listens for incoming connections on the specified port.
     * Returns once the server is bound to the port.
     *
     * @param port Port number to listen on
     * @return The WebSocket server instance for method chaining
     * @throws IllegalStateException    If the server is already running or still stopping, message encoder/decoder
     *                                  was not provided or the server could not be bound to the port
     * @throws IllegalArgumentException If the port is outside the valid range
     */
    public WebSocketServer<T, D> listen(int port) throws IllegalStateException {
        synchronized (lifecycleLock) {
            if (state == State.RUNNING) {
                throw new IllegalStateException("WebSocket server is already running on port " + port + "!");
            }
            if (state == State.STOPPING) {
                throw new IllegalStateException("WebSocket server is still stopping, cannot start it yet!");
            }
            if (configuration.getMessageDecoder() == null) {
                throw new IllegalStateException("Message decoder is not provided, cannot start the server!");
            }
            if (configuration.getMessageEncoder() == null) {
                throw new IllegalStateException("Message encoder is not provided, cannot start the server!");
            }

            EventLoopGroup boss = null;
            EventLoopGroup worker = null;
            boolean started = false;

            try {
                boss = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
                worker = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

                ChannelFuture bindFuture = new ServerBootstrap()
                    .group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new WebSocketServerChannelInitializer<>(this))
                    .bind(port)
                    .awaitUninterruptibly();

                if (!bindFuture.isSuccess()) {
                    throw new IllegalStateException("Failed to start WebSocket server on port " + port, bindFuture.cause());
                }

                bossGroup = boss;
                workerGroup = worker;
                serverChannel = bindFuture.channel();
                state = State.RUNNING;
                started = true;
            } finally {
                // Release the event loops of a failed startup, also when it fails before the bind result,
                // e.g. bind(int) throwing for an invalid port
                if (!started) {
                    shutdownGracefully(boss, worker);
                }
            }

            Channel channel = serverChannel;

            channel.closeFuture().addListener(_ -> {
                synchronized (lifecycleLock) {
                    if (serverChannel == channel) {
                        serverChannel = null;
                    }
                }
            });

            logger.info("Started WebSocket server at ws://localhost:{}", port);

            return this;
        }
    }

    /**
     * Stops the WebSocket server gracefully.
     * Closes the server channel, sends a going away close frame to every connected session and closes its connection,
     * then shuts down the event loop groups and waits for them to terminate.
     *
     * @throws IllegalStateException If the server is not running or the method is called from one of the server's
     *                               event loop threads, where waiting for the shutdown would block forever
     */
    public void stop() {
        Channel channel;
        EventLoopGroup boss;
        EventLoopGroup worker;

        synchronized (lifecycleLock) {
            if (state != State.RUNNING) {
                throw new IllegalStateException("Server is not running!");
            }
            if (isEventLoopThread(bossGroup, workerGroup)) {
                throw new IllegalStateException("Server cannot be stopped from its own event loop thread!");
            }
            logger.info("Stopping WebSocket server...");

            // STOPPING keeps listen() out until the resources below are released
            state = State.STOPPING;
            channel = serverChannel;
            boss = bossGroup;
            worker = workerGroup;
        }

        // Wait outside the lock, because the server channel close listener
        // runs on the boss event loop and needs the lock to complete.
        // The channel is null if it was closed without calling stop().
        try {
            if (channel != null) {
                channel.close().awaitUninterruptibly();
            }
            closeSessions();
        } finally {
            // Release the event loops and leave STOPPING even if closing the connections failed
            try {
                shutdownGracefully(boss, worker);
            } finally {
                synchronized (lifecycleLock) {
                    serverChannel = null;
                    bossGroup = null;
                    workerGroup = null;
                    state = State.STOPPED;
                }
            }
        }

        logger.info("Server stopped!");
    }

    /**
     * Sends a going away close frame to every connected session and waits until the clients
     * answer it or the close timeout elapses, then closes the connections that are still open.
     */
    private void closeSessions() {
        List<ChannelFuture> closeFutures = new ArrayList<>();
        Duration closeTimeout = configuration.getCloseTimeout();

        for (WebSocketSession<T, D> session : sessionRegistry.snapshot()) {
            Channel channel = session.getChannelContext().channel();
            CloseWebSocketFrame closeFrame = new CloseWebSocketFrame(WebSocketCloseStatus.ENDPOINT_UNAVAILABLE);

            closeFutures.add(ClosingHandshake.start(channel, closeFrame, closeTimeout));
        }

        long deadline = System.nanoTime() + closeTimeout.toNanos();

        for (ChannelFuture closeFuture : closeFutures) {
            Channel channel = closeFuture.channel();
            long remaining = Math.max(0, deadline - System.nanoTime());

            // The answer of a client served by the calling thread could only be read after stop() returns
            if (channel.eventLoop().inEventLoop() || !closeFuture.awaitUninterruptibly(remaining, TimeUnit.NANOSECONDS)) {
                channel.close();
            }
        }
    }

    /**
     * Shuts down the event loop groups and waits for them to terminate, skipping groups that were not created.
     * No quiet period is needed, sessions are already closed and the shutdown closes any remaining connections.
     */
    private static void shutdownGracefully(EventLoopGroup... groups) {
        for (EventLoopGroup group : groups) {
            if (group != null) {
                group.shutdownGracefully(0, SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        }

        for (EventLoopGroup group : groups) {
            if (group != null) {
                group.terminationFuture().awaitUninterruptibly();
            }
        }
    }

    private static boolean isEventLoopThread(EventLoopGroup... groups) {
        for (EventLoopGroup group : groups) {
            for (EventExecutor executor : group) {
                if (executor.inEventLoop()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Broadcasts a message to all connected WebSocket sessions.
     *
     * @param message The message to be broadcast
     */
    public synchronized void broadcast(T message) {
        if (!isRunning()) {
            throw new IllegalStateException("Server is not running, cannot broadcast!");
        }
        sessionRegistry.snapshot().forEach(session -> session.sendMessage(message));
    }

    /**
     * Returns a snapshot of all currently connected WebSocket sessions.
     * The returned collection is unmodifiable and does not change when sessions connect or disconnect later,
     * but the sessions in it are the live session objects.
     *
     * @return A collection of connected WebSocket sessions
     */
    public Collection<WebSocketSession<T, D>> getConnectedSessions() {
        return sessionRegistry.snapshot();
    }

    /**
     * Checks if the WebSocket server is currently running.
     * Returns false once the server channel has closed, even if stop() was not called yet.
     *
     * @return True if the server is running, false otherwise
     */
    public boolean isRunning() {
        return state == State.RUNNING && serverChannel != null;
    }

    /**
     * Returns the address the server is currently bound to. In particular, this exposes the port selected by the
     * operating system when the server was started with {@code listen(0)}.
     *
     * @return The bound local address, or null before the server starts and after its channel closes
     */
    public InetSocketAddress getLocalAddress() {
        Channel channel = serverChannel;
        return channel == null ? null : (InetSocketAddress) channel.localAddress();
    }

    /**
     * Retrieves a WebSocket session by its channel ID.
     *
     * @param id The channel ID of the session to retrieve
     * @return The WebSocket session associated with the given channel ID, or null if no session exists for the ID
     */
    WebSocketSession<T, D> getSessionByChannelId(ChannelId id) {
        return sessionRegistry.get(id);
    }

    /**
     * Removes a WebSocket session associated with the given channel ID
     * and unsubscribes it from all topics.
     *
     * @param id The channel ID of the session to remove
     */
    void removeSession(ChannelId id) {
        synchronized (sessionTopicLock) {
            WebSocketSession<T, D> session = sessionRegistry.remove(id);

            if (session != null) {
                topicBroker.unsubscribeAll(session);
            }
        }
    }

    /**
     * Adds a WebSocket session associated with its channel ID.
     *
     * @param session The WebSocket session to add
     */
    void addSession(WebSocketSession<T, D> session) {
        sessionRegistry.register(session);
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

    UpgradeHandler<D> getUpgradeHandler() {
        return upgradeHandler;
    }

    MessageHandler<T, D> getMessageHandler() {
        return messageHandler;
    }

    ErrorHandler<T, D> getErrorHandler() {
        return errorHandler;
    }

    CloseHandler<T, D> getCloseHandler() {
        return closeHandler;
    }

    WritabilityHandler<T, D> getWritabilityHandler() {
        return writabilityHandler;
    }

    WebSocketServerObserver<T, D> getObserver() {
        return observer;
    }
}
