package com.fan.lazyday.application.service;

import com.fan.lazyday.domain.event.QuotaExceededEvent;
import com.fan.lazyday.domain.tenant.repository.TenantRepository;
import com.fan.lazyday.domain.user.repository.UserRepository;
import com.fan.lazyday.infrastructure.event.DomainEventDeduplicator;
import com.fan.lazyday.infrastructure.event.DomainEventPublisher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaExceededEmailSubscriber {

    private final DomainEventPublisher domainEventPublisher;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final DomainEventDeduplicator domainEventDeduplicator;
    private Disposable subscription;

    @PostConstruct
    void subscribe() {
        subscription = domainEventPublisher.asFlux()
                .ofType(QuotaExceededEvent.class)
                .flatMap(this::handle)
                .subscribe(
                        ignored -> {
                        },
                        error -> log.error("Quota exceeded email subscriber terminated unexpectedly", error)
                );
    }

    @PreDestroy
    void dispose() {
        if (subscription != null) {
            subscription.dispose();
        }
    }

    Mono<Void> handle(QuotaExceededEvent event) {
        String dedupKey = "email:quota-exceeded:" + event.tenantId() + ":" + event.period();
        if (!domainEventDeduplicator.tryRecord(dedupKey)) {
            return Mono.empty();
        }
        return userRepository.findTenantAdminEmailsByTenantId(event.tenantId())
                .collectList()
                .flatMap(recipients -> {
                    if (recipients.isEmpty()) {
                        log.warn("quota exceeded email skipped - no admin found: tenantId={}", event.tenantId());
                        return Mono.empty();
                    }
                    return tenantRepository.findById(event.tenantId())
                            .flatMap(tenant -> emailService.send(
                                    recipients,
                                    "您的 Lazyday 配额已用尽",
                                    "quota-exceeded",
                                    Map.of(
                                            "tenantName", tenant.getName(),
                                            "period", event.period(),
                                            "limit", event.limit(),
                                            "usage", event.limit(),
                                            "portalUrl", "/quota"
                                    )
                            ));
                })
                .onErrorResume(ex -> {
                    log.warn("quota exceeded email failed: tenantId={}", event.tenantId(), ex);
                    return Mono.empty();
                });
    }
}
