package com.privchat.rooms.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TDD test for rooms-service JwtService.
 * Tests MUST FAIL before JwtService is implemented.
 */
class JwtServiceTest {

    private RSAPrivateKey privateKey;
    private RSAPublicKey  publicKey;
    private JwksClient    jwksClient;
    private JwtService    jwtService;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        privateKey = (RSAPrivateKey) kp.getPrivate();
        publicKey  = (RSAPublicKey)  kp.getPublic();

        // Build JwksClient from a real public key string
        jwksClient = new JwksClient(publicKey, true);
        jwtService = new JwtService(jwksClient);
    }

    private String signToken(String subject, Instant expiry) {
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry))
                .signWith(privateKey)
                .compact();
    }

    @Test
    void validateToken_validToken_returnsUsername() {
        String token = signToken("alice", Instant.now().plusSeconds(900));
        String username = jwtService.validateToken(token);
        assertThat(username).isEqualTo("alice");
    }

    @Test
    void validateToken_expiredToken_throws() {
        String token = signToken("alice", Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> jwtService.validateToken(token))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void validateToken_tamperedSignature_throws() {
        String token = signToken("alice", Instant.now().plusSeconds(900));
        // Tamper with the signature part
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".invalidsignature";
        assertThatThrownBy(() -> jwtService.validateToken(tampered))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void validateToken_missingSubClaim_throws() {
        // Token without sub claim
        String token = Jwts.builder()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(privateKey)
                .compact();
        assertThatThrownBy(() -> jwtService.validateToken(token))
                .isInstanceOf(Exception.class);
    }
}
