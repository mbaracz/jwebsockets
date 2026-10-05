package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import javax.net.ssl.SSLException;
import java.security.Security;
import java.security.cert.CertificateException;

import static org.assertj.core.api.Assertions.assertThat;

class SslTest {

    private WebSocketServer<String, Object> server;

    @BeforeEach
    void setUp() {
        server = new WebSocketServer<String, Object>()
                .configure(configurer -> configurer
                        .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                        .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                );
    }

    @Test
    void shouldKeepChannelOpenAndActiveWhenHandshakeIsSent() throws CertificateException, SSLException {
        // Add Bouncy Castle as a security provider
        Security.addProvider(new BouncyCastleProvider());

        // Generate a self-signed certificate for testing
        SelfSignedCertificate cert = new SelfSignedCertificate();
        SslContext sslContext = SslContextBuilder.forServer(cert.certificate(), cert.privateKey()).build();

        EmbeddedChannel channel = Util.newEmbeddedChannel(new WebSocketServerHandler<>(server));
        channel.pipeline().addFirst(sslContext.newHandler(channel.alloc()));

        Util.performHandshake(channel, "/");

        // Assert expected behaviour
        assertThat(channel.isOpen()).as("Channel should be opened").isTrue();
        assertThat(channel.isActive()).as("Channel should be active").isTrue();
    }
}
