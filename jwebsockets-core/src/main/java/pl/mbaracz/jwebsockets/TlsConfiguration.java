package pl.mbaracz.jwebsockets;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Provider-neutral server TLS credentials. The runtime selects the TLS provider when the server starts.
 */
public final class TlsConfiguration {

    private final Path certificateChain;
    private final Path privateKey;
    private final String privateKeyPassword;

    private TlsConfiguration(Path certificateChain, Path privateKey, String privateKeyPassword) {
        this.certificateChain = Objects.requireNonNull(certificateChain, "Certificate chain must not be null!");
        this.privateKey = Objects.requireNonNull(privateKey, "Private key must not be null!");
        this.privateKeyPassword = privateKeyPassword;
    }

    /**
     * Creates a TLS configuration from an X.509 certificate chain and an unencrypted PKCS#8 private key in PEM format.
     *
     * @param certificateChain path to the certificate chain in PEM format.
     * @param privateKey       path to the PKCS#8 private key in PEM format.
     * @return the TLS configuration.
     */
    public static TlsConfiguration forPem(Path certificateChain, Path privateKey) {
        return forPem(certificateChain, privateKey, null);
    }

    /**
     * Creates a TLS configuration from an X.509 certificate chain and a PKCS#8 private key in PEM format.
     *
     * @param certificateChain   path to the certificate chain in PEM format.
     * @param privateKey         path to the PKCS#8 private key in PEM format.
     * @param privateKeyPassword password of the private key, or null if it is not encrypted.
     * @return the TLS configuration.
     */
    public static TlsConfiguration forPem(Path certificateChain, Path privateKey, String privateKeyPassword) {
        return new TlsConfiguration(certificateChain, privateKey, privateKeyPassword);
    }

    Path certificateChain() {
        return certificateChain;
    }

    Path privateKey() {
        return privateKey;
    }

    String privateKeyPassword() {
        return privateKeyPassword;
    }
}
