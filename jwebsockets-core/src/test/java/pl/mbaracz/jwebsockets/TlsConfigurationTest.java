package pl.mbaracz.jwebsockets;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TlsConfigurationTest {

    private static final Path CERTIFICATE_CHAIN = Path.of("certificate.pem");
    private static final Path PRIVATE_KEY = Path.of("private-key.pem");

    @Test
    void shouldCreateConfigurationForUnencryptedPemPrivateKey() {
        TlsConfiguration configuration = TlsConfiguration.forPem(CERTIFICATE_CHAIN, PRIVATE_KEY);

        assertThat(configuration.certificateChain()).isEqualTo(CERTIFICATE_CHAIN);
        assertThat(configuration.privateKey()).isEqualTo(PRIVATE_KEY);
        assertThat(configuration.privateKeyPassword()).isNull();
    }

    @Test
    void shouldCreateConfigurationForEncryptedPemPrivateKey() {
        TlsConfiguration configuration = TlsConfiguration.forPem(CERTIFICATE_CHAIN, PRIVATE_KEY, "secret");

        assertThat(configuration.certificateChain()).isEqualTo(CERTIFICATE_CHAIN);
        assertThat(configuration.privateKey()).isEqualTo(PRIVATE_KEY);
        assertThat(configuration.privateKeyPassword()).isEqualTo("secret");
    }

    @Test
    void shouldRequireCertificateAndPrivateKeyPaths() {
        assertThatNullPointerException().isThrownBy(() -> TlsConfiguration.forPem(null, PRIVATE_KEY));
        assertThatNullPointerException().isThrownBy(() -> TlsConfiguration.forPem(CERTIFICATE_CHAIN, null));
    }
}
