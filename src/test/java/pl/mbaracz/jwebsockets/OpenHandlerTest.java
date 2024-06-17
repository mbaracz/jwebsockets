package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenHandlerTest {

    @Test
    public void When_HandshakeIsDone_Then_ShouldCallOpenHandler() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        WebSocketServer<String, Object> server = new WebSocketServer<String, Object>()
                .configure(configurer -> configurer
                        .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                        .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                )
                .onOpen((session) -> latch.countDown());

        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        channel.pipeline().addFirst(new HttpServerCodec());
        Util.performHandshake(channel, "/");

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Open handler was not called");
    }
}
