package com.fan.lazyday.infrastructure.event;

import com.fan.lazyday.domain.event.DomainEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
public class DomainEventPublisher {

    private static final int DEFAULT_BUFFER_SIZE = 10_000;

    private final Sinks.Many<DomainEvent> sink;
    @Nullable
    private final MeterRegistry meterRegistry;

    @Autowired
    public DomainEventPublisher(@Nullable MeterRegistry meterRegistry) {
        this(DEFAULT_BUFFER_SIZE, meterRegistry);
    }

    DomainEventPublisher(int bufferSize, @Nullable MeterRegistry meterRegistry) {
        this.sink = Sinks.many().multicast().onBackpressureBuffer(bufferSize);
        this.meterRegistry = meterRegistry;
    }

    public Sinks.EmitResult publish(DomainEvent event) {
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
            log.warn("Domain event publish dropped because webhook event buffer is full: eventType={}, tenantId={}",
                    event.eventType(), event.tenantId());
            if (meterRegistry != null) {
                meterRegistry.counter("lazyday.webhook.publish.dropped").increment();
            }
        }
        return result;
    }

    public Flux<DomainEvent> asFlux() {
        return sink.asFlux();
    }
}
