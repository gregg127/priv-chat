package com.privchat.auth.service;

import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Issues RS256 JWTs and exposes the RSA public key for JWKS distribution.
 *
 * <p>Key loading:
 * <ul>
 *   <li>If {@code JWT_PRIVATE_KEY} and {@code JWT_PUBLIC_KEY} are non-blank base64-encoded PEM
 *       PKCS#8 / X.509 DER keys, they are loaded from those env vars.</li>
 *   <li>If either is blank, an ephemeral RSA 2048 key pair is generated at startup.
 *       This is intended for local development / testing only — tokens from a previous
 *       instance will be invalid after restart.</li>
 * </ul>
 *
 * <p>The private key is NEVER logged. Only the public key is exposed via
 * {@link #getPublicKey()} for the JWKS endpoint.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey  publicKey;
    private final long          expirySeconds;

    public JwtService(
            @Value("${JWT_PRIVATE_KEY:}") String privateKeyBase64,
            @Value("${JWT_PUBLIC_KEY:}")  String publicKeyBase64,
            @Value("${JWT_EXPIRY_SECONDS:900}") long expirySeconds) {

        this.expirySeconds = expirySeconds;

        if (privateKeyBase64.isBlank() || publicKeyBase64.isBlank()) {
            log.warn("JWT_PRIVATE_KEY / JWT_PUBLIC_KEY not configured — generating ephemeral RSA key pair. " +
                     "Tokens will be invalidated on restart. Configure keys for production.");
            KeyPair kp = generateEphemeralKeyPair();
            this.privateKey = (RSAPrivateKey) kp.getPrivate();
            this.publicKey  = (RSAPublicKey)  kp.getPublic();
        } else {
            try {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                byte[] privBytes = Base64.getDecoder().decode(privateKeyBase64.strip());
                byte[] pubBytes  = Base64.getDecoder().decode(publicKeyBase64.strip());
                this.privateKey = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
                this.publicKey  = (RSAPublicKey)  kf.generatePublic(new X509EncodedKeySpec(pubBytes));
                log.info("JWT RSA key pair loaded from environment variables.");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load RSA key pair from environment variables", e);
            }
        }
    }

    /**
     * Generates a signed RS256 JWT with {@code sub=username}, {@code iat=now},
     * {@code exp=now+expirySeconds}.
     */
    public String generateToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirySeconds)))
                .signWith(privateKey)
                .compact();
    }

    /**
     * Returns the RSA public key for JWKS distribution.
     * The private key is never exposed through this or any other method.
     */
    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    private static KeyPair generateEphemeralKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate ephemeral RSA key pair", e);
        }
    }
}
