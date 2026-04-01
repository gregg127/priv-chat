package com.privchat.rooms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

/**
 * Validates RS256 JWTs issued by entry-auth-service.
 *
 * <p>Verifies the JWT signature using the RSA public key fetched at startup by
 * {@link JwksClient}. No per-request network calls — fully stateless.
 */
@Service
public class JwtService {

    private final JwksClient jwksClient;

    public JwtService(JwksClient jwksClient) {
        this.jwksClient = jwksClient;
    }

    /**
     * Validates the token and returns the username (JWT {@code sub} claim).
     *
     * @param token Bearer token string (without "Bearer " prefix)
     * @return username extracted from {@code sub} claim
     * @throws io.jsonwebtoken.JwtException if the token is invalid, expired, or tampered
     * @throws IllegalArgumentException if the {@code sub} claim is missing
     */
    public String validateToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(jwksClient.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT missing required 'sub' claim");
        }
        return subject;
    }
}
