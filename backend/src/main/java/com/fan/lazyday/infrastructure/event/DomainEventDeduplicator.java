package com.fan.lazyday.infrastructure.event;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class DomainEventDeduplicator {

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final long DEFAULT_MAX_SIZE = 10_000L;

    private final Cache<String, Boolean> cache;

    public DomainEventDeduplicator() {
        this(DEFAULT_TTL, DEFAULT_MAX_SIZE);
    }

    DomainEventDeduplicator(Duration ttl, long maximumSize) {
        this(ttl, maximumSize, Ticker.systemTicker());
    }

    DomainEventDeduplicator(Duration ttl, long maximumSize, Ticker ticker) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maximumSize)
                .ticker(ticker)
                .build();
    }

    public boolean tryRecord(String key) {
        Boolean previous = cache.asMap().putIfAbsent(key, Boolean.TRUE);
        return previous == null;
    }
}
