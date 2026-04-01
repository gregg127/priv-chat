package com.privchat.auth.controller;

import com.privchat.auth.service.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * GET /auth/jwks
 * Returns the RSA public key in JWK Set format for use by downstream services
 * (e.g. rooms-service) to verify RS256 JWTs locally.
 * This endpoint is unauthenticated — no credentials required.
 */
@RestController
@RequestMapping("/auth")
public class JwksController {

    private final JwtService jwtService;

    public JwksController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @GetMapping("/jwks")
    public ResponseEntity<Map<String, Object>> jwks() {
        RSAPublicKey key = jwtService.getPublicKey();

        // Encode modulus and exponent as base64url (no padding)
        String n = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(key.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(key.getPublicExponent().toByteArray());

        Map<String, Object> jwk = Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", "priv-chat-1",
                "n", n,
                "e", e
        );

        return ResponseEntity.ok(Map.of("keys", List.of(jwk)));
    }
}
