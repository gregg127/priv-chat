package com.privchat.rooms.config;

import com.privchat.rooms.security.JwksClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;

/**
 * Test configuration that provides a mock JwksClient.
 * Prevents the real JwksClient from trying to contact entry-auth-service during tests.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public JwksClient testJwksClient() throws Exception {
        // Generate a real RSA key pair for testing
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        RSAPublicKey testPublicKey = (RSAPublicKey) gen.generateKeyPair().getPublic();

        // Build a JWKS JSON from the test key and use the factory method
        String n = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(testPublicKey.getModulus().toByteArray());
        String e = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(testPublicKey.getPublicExponent().toByteArray());
        String jwksJson = """
                {"keys":[{"kty":"RSA","use":"sig","alg":"RS256","kid":"test","n":"%s","e":"%s"}]}
                """.formatted(n, e);
        return JwksClient.fromJson(jwksJson);
    }
}
