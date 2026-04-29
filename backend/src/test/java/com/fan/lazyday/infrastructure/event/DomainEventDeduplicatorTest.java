package com.fan.lazyday.infrastructure.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventDeduplicatorTest {

    @Test
    @DisplayName("同一 key 在窗口内只记录一次，过期后可再次记录")
    void tryRecord_shouldSuppressDuplicatesUntilExpired() {
        AtomicLong tickerNanos = new AtomicLong();
        DomainEventDeduplicator deduplicator = new DomainEventDeduplicator(
                Duration.ofHours(24),
                10,
                tickerNanos::get
        );

        assertThat(deduplicator.tryRecord("tenant:1:quota:day")).isTrue();
        assertThat(deduplicator.tryRecord("tenant:1:quota:day")).isFalse();

        tickerNanos.addAndGet(TimeUnit.HOURS.toNanos(25));

        assertThat(deduplicator.tryRecord("tenant:1:quota:day")).isTrue();
    }
}
