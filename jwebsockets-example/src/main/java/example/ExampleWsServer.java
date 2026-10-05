package example;

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

import java.util.Optional;

/**
 * Demonstrates cookie authentication, session context and pub/sub messaging.
 */
public class ExampleWsServer {

    private static final int PORT = 8080;

    private static final Logger LOGGER = LoggerFactory.getLogger(ExampleWsServer.class);

    private static final WebSocketServer<String, PerSocketData> SERVER = new WebSocketServer<>();

    private static final UpgradeHandler<PerSocketData> UPGRADE_HANDLER = (request, response) -> {
        // A rejected upgrade returns Bad Request unless the handler customizes the response.
        String token = request.getCookie("token");
        if (token == null) {
            return UpgradeResult.reject();
        }

        Optional<User> userOptional = UserManager.INSTANCE.findUserByToken(token);
        if (userOptional.isEmpty()) {
            return UpgradeResult.reject();
        }

        User user = userOptional.get();

        return UpgradeResult.accept(new PerSocketData(user.getId(), user.getName()));
    };

    private static final OpenHandler<String, PerSocketData> OPEN_HANDLER = (session) -> {
        String name = session.getContext().name();

        LOGGER.info("New user connected: {}", session.getContext().name());

        SERVER.subscribe(session, "general");

        SERVER.publish("general", String.format("%s just connected, say hi to him!", name));
    };

    private static final CloseHandler<String, PerSocketData> CLOSE_HANDLER = (session, reason, code) -> {
        if (session.getContext() != null) {
            LOGGER.info("{} disconnected", session.getContext().name());
        }

        // Closed sessions are automatically removed from their topics.
        SERVER.publish("general", String.format("%s disconnected", session.getContext().name()));
    };

    private static final MessageHandler<String, PerSocketData> MESSAGE_HANDLER = (session, message) -> {
        LOGGER.info("Received message from {}: {}", session.getContext().name(), message);
        SERVER.publish("general", String.format("%s: %s", session.getContext().name(), message));
    };

    static void main() {
        SERVER.configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .onUpgrade(UPGRADE_HANDLER)
            .onMessage(MESSAGE_HANDLER)
            .onOpen(OPEN_HANDLER)
            .onClose(CLOSE_HANDLER)
            .listen(PORT);
    }
}
