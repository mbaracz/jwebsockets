package pl.mbaracz.jwebsockets;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SessionWritabilityTest {

    private static WebSocketServer<String, Object> createServer(List<WebSocketSession<String, Object>> opened) {
        return new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
            )
            .onOpen(opened::add);
    }

    /**
     * Holds back flushes like a client that stops reading, so written messages stay in the write buffer.
     */
    private static void holdFlushes(EmbeddedChannel channel) {
        channel.pipeline().addFirst(new ChannelOutboundHandlerAdapter() {
            @Override
            public void flush(ChannelHandlerContext context) {
            }
        });
    }

    @Test
    public void When_SessionIsConnected_Then_ShouldInitiallyBeWritable() {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(opened));

        assertTrue(opened.getFirst().isWritable(), "Session should be writable");

        channel.close();
    }

    @Test
    public void When_WriteBufferExceedsHighWaterMark_Then_SessionShouldNotBeWritableUntilFlushed() {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(opened));
        WebSocketSession<String, Object> session = opened.getFirst();

        channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(1024, 2048));
        holdFlushes(channel);

        session.sendMessage("x".repeat(4096));

        assertTrue(channel.isOpen(), "Channel should stay open");
        assertFalse(session.isWritable(), "Session should not be writable above the high water mark");

        // Flush the buffered message
        channel.pipeline().removeFirst();
        channel.flush();

        assertTrue(session.isWritable(), "Session should be writable again below the low water mark");

        channel.close();
    }

    @Test
    public void When_WriteBufferCrossesWaterMarks_Then_WritabilityHandlerShouldBeNotified() {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        List<Boolean> notifications = new ArrayList<>();

        WebSocketServer<String, Object> server = createServer(opened)
            .onWritabilityChanged((_, writable) -> notifications.add(writable));

        EmbeddedChannel channel = Util.connect(server);
        channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(1024, 2048));
        holdFlushes(channel);

        opened.getFirst().sendMessage("x".repeat(4096));

        assertEquals(List.of(false), notifications, "Should be notified when the high water mark is exceeded");

        // Flush the buffered message
        channel.pipeline().removeFirst();
        channel.flush();

        assertEquals(List.of(false, true), notifications, "Should be notified when the buffer drops below the low water mark");

        channel.close();
    }

    @Test
    public void When_ChannelIsClosed_Then_SessionShouldNotBeWritable() {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        EmbeddedChannel channel = Util.connect(createServer(opened));

        // Close the connection
        channel.close();

        assertFalse(opened.getFirst().isWritable(), "Session should not be writable");
    }
}
