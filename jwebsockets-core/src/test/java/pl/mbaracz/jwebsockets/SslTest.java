package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import javax.net.ssl.SSLException;
import java.security.Security;
import java.security.cert.CertificateException;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SslTest {

    private static WebSocketServer<String, Object> server;

    @BeforeAll
    public static void setUp() {
        server = new WebSocketServer<String, Object>()
                .configure(configurer -> configurer
                        .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                        .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                );
    }

    @Test
    public void When_HandshakeIsSent_Then_ExpectChannelIsOpen_And_Active() throws CertificateException, SSLException {
        // Add Bouncy Castle as a security provider
        Security.addProvider(new BouncyCastleProvider());

        // Generate a self-signed certificate for testing
        SelfSignedCertificate cert = new SelfSignedCertificate();
        SslContext sslContext = SslContextBuilder.forServer(cert.certificate(), cert.privateKey()).build();

        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerHandler<>(server));
        channel.pipeline().addFirst(sslContext.newHandler(channel.alloc()));

        Util.performHandshake(channel, "/");

        // Assert expected behaviour
        assertTrue(channel.isOpen(), "Channel should be opened");
        assertTrue(channel.isActive(), "Channel should be active");
    }
}
