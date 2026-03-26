package com.privchat.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimitService {

    private final Cache<String, Bucket> bucketCache;

    public RateLimitService() {
        this.bucketCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();
    }

    public boolean tryConsume(String ipAddress) {
        Bucket bucket = bucketCache.get(ipAddress, this::newBucket);
        return bucket.tryConsume(1);
    }

    private Bucket newBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(10)));
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }
}
