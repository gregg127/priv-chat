package com.privchat.rooms.security;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * TDD test for JwksClient.
 * Tests MUST FAIL before JwksClient is implemented.
 */
class JwksClientTest {

    /**
     * Builds a JWKS JSON string from a real RSA public key.
     */
    private String buildJwksJson(RSAPublicKey publicKey) {
        String n = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(publicKey.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(publicKey.getPublicExponent().toByteArray());
        return """
                {
                  "keys": [{
                    "kty": "RSA",
                    "use": "sig",
                    "alg": "RS256",
                    "kid": "priv-chat-1",
                    "n": "%s",
                    "e": "%s"
                  }]
                }
                """.formatted(n, e);
    }

    @Test
    void getPublicKey_returnsValidRSAPublicKey() throws Exception {
        // Generate a real RSA key pair for test
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        RSAPublicKey original = (RSAPublicKey) kp.getPublic();

        String jwksJson = buildJwksJson(original);

        // Use JwksClient with a mock HTTP response
        JwksClient client = JwksClient.fromJson(jwksJson);
        RSAPublicKey result = client.getPublicKey();

        assertThat(result).isNotNull();
        assertThat(result.getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void getPublicKey_hasCorrectModulusAndExponent() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        RSAPublicKey original = (RSAPublicKey) kp.getPublic();

        String jwksJson = buildJwksJson(original);
        JwksClient client = JwksClient.fromJson(jwksJson);
        RSAPublicKey result = client.getPublicKey();

        assertThat(result.getModulus()).isEqualTo(original.getModulus());
        assertThat(result.getPublicExponent()).isEqualTo(original.getPublicExponent());
    }
}
