package com.fan.lazyday.infrastructure.event;

import com.fan.lazyday.domain.event.QuotaExceededEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventPublisherTest {

    @Test
    @DisplayName("领域事件发布后多个订阅者都能收到")
    void publish_shouldFanOutToMultipleSubscribers() {
        DomainEventPublisher publisher = new DomainEventPublisher(new SimpleMeterRegistry());
        QuotaExceededEvent event = new QuotaExceededEvent(1L, "day", 100L, 100L, Instant.parse("2026-04-29T00:00:00Z"));

        StepVerifier.create(publisher.asFlux().take(1))
                .then(() -> StepVerifier.create(publisher.asFlux().take(1))
                        .then(() -> assertThat(publisher.publish(event)).isEqualTo(Sinks.EmitResult.OK))
                        .expectNext(event)
                        .verifyComplete())
                .expectNext(event)
                .verifyComplete();
    }

    @Test
    @DisplayName("buffer 满时 publish 返回 FAIL_OVERFLOW 且递增丢弃指标")
    void publish_whenBufferFull_shouldReturnOverflowAndIncrementCounter() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DomainEventPublisher publisher = new DomainEventPublisher(1, meterRegistry);
        QuotaExceededEvent first = new QuotaExceededEvent(1L, "day", 100L, 100L, Instant.now());
        QuotaExceededEvent second = new QuotaExceededEvent(1L, "month", 1000L, 1000L, Instant.now());

        publisher.asFlux().subscribe(new BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                // Keep demand at zero so the one-slot backpressure buffer fills.
            }

            @Override
            protected void hookFinally(SignalType type) {
            }
        });

        assertThat(publisher.publish(first)).isEqualTo(Sinks.EmitResult.OK);
        assertThat(publisher.publish(second)).isEqualTo(Sinks.EmitResult.FAIL_OVERFLOW);
        assertThat(meterRegistry.counter("lazyday.webhook.publish.dropped").count()).isEqualTo(1.0d);
    }
}
