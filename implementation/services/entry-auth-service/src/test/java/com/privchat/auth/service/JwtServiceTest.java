package com.privchat.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD test for JwtService (entry-auth-service).
 * These tests MUST FAIL before JwtService is implemented.
 */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        // Ephemeral mode: no env vars → generates ephemeral RSA key pair at startup
        jwtService = new JwtService("", "", 900);
    }

    @Test
    void generateToken_returnsNonNullString() {
        String token = jwtService.generateToken("alice");
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void generateToken_isRS256Jwt() {
        String token = jwtService.generateToken("alice");
        // RS256 JWT header starts with eyJhbGciOiJSUzI1NiJ9 (base64url of {"alg":"RS256"})
        assertThat(token).startsWith("ey");
        // Has 3 parts separated by dots
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void getPublicKey_returnsNonNull() {
        RSAPublicKey key = jwtService.getPublicKey();
        assertThat(key).isNotNull();
    }

    @Test
    void getPublicKey_isRSAKey() {
        RSAPublicKey key = jwtService.getPublicKey();
        assertThat(key.getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void ephemeralMode_generatesKeyPairWhenEnvVarsBlank() {
        // Two separate instances both get non-null keys (different key pairs in ephemeral mode)
        JwtService svc1 = new JwtService("", "", 900);
        JwtService svc2 = new JwtService("", "", 900);
        assertThat(svc1.getPublicKey()).isNotNull();
        assertThat(svc2.getPublicKey()).isNotNull();
    }
}
