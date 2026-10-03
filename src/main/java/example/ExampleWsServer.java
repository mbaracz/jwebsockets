package example;

import io.netty.handler.codec.http.HttpHeaderNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.mbaracz.jwebsockets.WebSocketServer;
import pl.mbaracz.jwebsockets.handler.CloseHandler;
import pl.mbaracz.jwebsockets.handler.MessageHandler;
import pl.mbaracz.jwebsockets.handler.OpenHandler;
import pl.mbaracz.jwebsockets.handler.UpgradeHandler;
import pl.mbaracz.jwebsockets.handler.UpgradeResult;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.List;
import java.util.Optional;

/**
 * The ExampleWsServer class configures and starts a WebSocket server with various event handlers.
 */
public class ExampleWsServer {

    // Define the port number on which the WebSocket server will listen
    private static final int port = 8080;

    // Create a logger for logging purposes
    private static final Logger logger = LoggerFactory.getLogger(ExampleWsServer.class);

    // Initialize the WebSocket server
    private static final WebSocketServer<String, PerSocketData> server = new WebSocketServer<>();

    // Handler to manage WebSocket upgrade requests
    private static final UpgradeHandler<PerSocketData> upgradeHandler = (request, response) -> {
        // Retrieve cookies from the request headers
        List<String> cookies = request.headers().getAll(HttpHeaderNames.COOKIE);

        // If no cookies are present, reject the upgrade request
        // Bad request is sent by default, you can modify response if needed
        if (cookies.isEmpty()) {
            return UpgradeResult.reject();
        }

        // Find the 'token' cookie value
        String token = HttpUtil.findCookieValue(cookies, "token");
        if (token == null) {
            return UpgradeResult.reject();
        }

        // Try to find a user associated with the token
        Optional<User> userOptional = UserManager.INSTANCE.findUserByToken(token);
        if (userOptional.isEmpty()) {
            return UpgradeResult.reject();
        }

        // Get the user object
        User user = userOptional.get();

        // Approve the upgrade request with session data holding the user information
        return UpgradeResult.accept(new PerSocketData(user.getId(), user.getName()));
    };

    // Handler to manage WebSocket connection open events
    private static final OpenHandler<String, PerSocketData> openHandler = (session) -> {
        // Retrieve the user's name from session data
        String name = session.getContext().getName();
        logger.info(String.format("New user connected: %s", name));

        // Subscribe the session to the "general" channel
        server.subscribe(session, "general");

        // Publish a welcome message to the "general" channel
        server.publish("general", String.format("%s just connected, say hi to him!", name));
    };

    // Handler to manage WebSocket connection close events
    private static final CloseHandler<String, PerSocketData> closeHandler = (session, reason, code) -> {
        if (session.getContext() != null) {
            logger.info(String.format("%s disconnected", session.getContext().getName()));
        }
        // Publish a disconnection message to the "general" channel
        // No need to unsubscribe, done automatically
        server.publish("general", String.format("%s disconnected", session.getContext().getName()));
    };

    // Handler to manage incoming WebSocket messages
    private static final MessageHandler<String, PerSocketData> messageHandler = (session, message) -> {
        logger.info(String.format("Received message from %s: %s", session.getContext().getName(), message));
        // Publish the received message to the "general" channel
        server.publish("general", String.format("%s: %s", session.getContext().getName(), message));
    };

    // Main method to configure and start the WebSocket server
    public static void main(String[] args) {
        server.configure(configurer -> configurer
                        // Set the message decoder and encoder
                        .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                        .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                )
                // Set the handlers for various WebSocket events
                .onUpgrade(upgradeHandler)
                .onMessage(messageHandler)
                .onOpen(openHandler)
                .onClose(closeHandler)
                // Start the server and listen on the defined port
                .listen(port);
    }
}
