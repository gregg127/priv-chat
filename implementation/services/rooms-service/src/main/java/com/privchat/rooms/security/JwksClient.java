package com.privchat.rooms.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * Fetches and caches the RSA public key from the entry-auth-service JWKS endpoint.
 *
 * <p>The key is fetched once at startup via {@link #fetchAndCacheKey()} and then
 * served from a volatile field for all JWT validation calls. No per-request HTTP
 * calls are made to entry-auth-service.
 */
@Component
public class JwksClient {

    private static final Logger log = LoggerFactory.getLogger(JwksClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private volatile RSAPublicKey cachedPublicKey;

    public JwksClient(@Value("${entry-auth-service.url:http://entry-auth-service:8080}") String entryAuthUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(entryAuthUrl)
                .build();
    }

    /** Constructor for testing — accepts pre-parsed JWKS JSON directly. */
    private JwksClient(RSAPublicKey key) {
        this.restClient = null;
        this.cachedPublicKey = key;
    }

    /** Package-private constructor for unit testing with a raw RSAPublicKey. */
    JwksClient(RSAPublicKey key, boolean forTesting) {
        this.restClient = null;
        this.cachedPublicKey = key;
    }

    @PostConstruct
    public void fetchAndCacheKey() {
        if (restClient == null) return; // test mode
        try {
            String jwksJson = restClient.get()
                    .uri("/auth/jwks")
                    .retrieve()
                    .body(String.class);
            this.cachedPublicKey = parseFirstKey(jwksJson);
            log.info("JWKS public key fetched and cached from entry-auth-service.");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch JWKS from entry-auth-service", e);
        }
    }

    /**
     * Returns the cached RSA public key.
     */
    public RSAPublicKey getPublicKey() {
        return cachedPublicKey;
    }

    /**
     * Factory method for testing — parses JWKS JSON directly without HTTP.
     */
    public static JwksClient fromJson(String jwksJson) {
        try {
            RSAPublicKey key = parseFirstKey(jwksJson);
            return new JwksClient(key);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse JWKS JSON", e);
        }
    }

    static RSAPublicKey parseFirstKey(String jwksJson) throws Exception {
        JsonNode root = MAPPER.readTree(jwksJson);
        JsonNode firstKey = root.get("keys").get(0);

        byte[] nBytes = Base64.getUrlDecoder().decode(firstKey.get("n").asText());
        byte[] eBytes = Base64.getUrlDecoder().decode(firstKey.get("e").asText());

        BigInteger modulus  = new BigInteger(1, nBytes);
        BigInteger exponent = new BigInteger(1, eBytes);

        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) kf.generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }
}
