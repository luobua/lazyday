package com.fan.lazyday.application.service.braindispatch;

import com.fan.lazyday.domain.braindispatch.repository.BrainDispatchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrainDispatchTimeoutScanner {

    private final BrainDispatchLogRepository repository;
    private Disposable subscription;

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        subscription = Flux.interval(Duration.ofSeconds(5))
                .publishOn(Schedulers.boundedElastic())
                .flatMap(tick -> scanOnce())
                .onErrorContinue((error, value) -> log.warn("Brain dispatch timeout scan failed", error))
                .subscribe();
    }

    public reactor.core.publisher.Mono<Long> scanOnce() {
        return repository.markTimeoutBefore(Instant.now().minusSeconds(30));
    }

    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }
}
