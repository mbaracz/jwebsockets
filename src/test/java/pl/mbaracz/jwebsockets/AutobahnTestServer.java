package pl.mbaracz.jwebsockets;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/**
 * Echo server tested by the Autobahn WebSocket Testsuite, see {@code autobahn/README.md}.
 * Autobahn expects every message back with the same payload and the same frame type: text as text, binary as binary.
 */
public final class AutobahnTestServer {

    private static final int PORT = 9001;

    /**
     * Frame type of the message being handled, kept as the session data.
     */
    private enum MessageType {
        TEXT,
        BINARY
    }

    static void main() {
        WebSocketServer<byte[], MessageType> server = new WebSocketServer<byte[], MessageType>()
            .configure(configurer -> configurer
                .setMessageDecoder(data -> data)
                .setMessageEncoder(message -> message)
                .setAllowBinaryFrames(true)
            )
            .onOpen(AutobahnTestServer::recordMessageTypes)
            .onMessage(AutobahnTestServer::echo);

        server.listen(PORT);
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(server::stop));

        System.out.println("jwebsockets listening on :" + PORT);
    }

    /**
     * Stores the frame type of every message in the session data before the server's
     * handler decodes it, because the message handler receives only the payload.
     */
    private static void recordMessageTypes(WebSocketSession<byte[], MessageType> session) {
        ChannelHandlerContext serverHandler = session.getContext();

        serverHandler.pipeline().addBefore(serverHandler.name(), "autobahnMessageType", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext context, Object message) {
                if (message instanceof TextWebSocketFrame) {
                    session.setData(MessageType.TEXT);
                } else if (message instanceof BinaryWebSocketFrame) {
                    session.setData(MessageType.BINARY);
                }
                context.fireChannelRead(message);
            }
        });
    }

    /**
     * Sends the message back in a frame of the type it arrived in.
     * The server's handler calls this on the event loop right after
     * the frame passed the recorder, so the session data describes this message.
     */
    private static void echo(WebSocketSession<byte[], MessageType> session, byte[] payload) {
        WebSocketFrame frame = switch (session.getData()) {
            case TEXT -> new TextWebSocketFrame(Unpooled.wrappedBuffer(payload));
            case BINARY -> new BinaryWebSocketFrame(Unpooled.wrappedBuffer(payload));
        };

        // sendMessage() would use the frame type configured for the whole server, so the frame is written directly
        session.getContext().writeAndFlush(frame);
    }
}
