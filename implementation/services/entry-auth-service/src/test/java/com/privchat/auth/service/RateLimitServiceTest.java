package com.privchat.auth.service;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
    }

    @Test
    void allowsFirst5AttemptsFromSameIp() {
        String ip = "10.0.0.1";
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimitService.tryConsume(ip))
                .as("Attempt %d should be allowed", i + 1)
                .isTrue();
        }
    }

    @Test
    void blocksOnSixthAttemptFromSameIp() {
        String ip = "10.0.0.2";
        // Consume all 5 tokens
        for (int i = 0; i < 5; i++) {
            rateLimitService.tryConsume(ip);
        }
        // 6th should be blocked
        assertThat(rateLimitService.tryConsume(ip)).isFalse();
    }

    @Test
    void differentIpsHaveIndependentBuckets() {
        String ip1 = "10.0.0.3";
        String ip2 = "10.0.0.4";

        // Exhaust ip1
        for (int i = 0; i < 5; i++) {
            rateLimitService.tryConsume(ip1);
        }
        assertThat(rateLimitService.tryConsume(ip1)).isFalse();

        // ip2 should still have tokens
        assertThat(rateLimitService.tryConsume(ip2)).isTrue();
    }

    @Test
    void rateLimitedAttemptsAreTracked() {
        String ip = "10.0.0.5";
        for (int i = 0; i < 5; i++) {
            rateLimitService.tryConsume(ip);
        }
        // Multiple blocked attempts should all return false
        assertThat(rateLimitService.tryConsume(ip)).isFalse();
        assertThat(rateLimitService.tryConsume(ip)).isFalse();
    }
}
