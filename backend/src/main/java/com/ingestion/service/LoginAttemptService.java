package com.ingestion.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long BLOCK_DURATION_MINUTES = 15;

    private final Cache<String, AtomicInteger> attempts = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(BLOCK_DURATION_MINUTES, TimeUnit.MINUTES)
            .build();

    public boolean isBlocked(String username) {
        AtomicInteger count = attempts.getIfPresent(normalize(username));
        return count != null && count.get() >= MAX_ATTEMPTS;
    }

    public void recordFailure(String username) {
        attempts.asMap()
                .computeIfAbsent(normalize(username), k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    public void recordSuccess(String username) {
        attempts.invalidate(normalize(username));
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
