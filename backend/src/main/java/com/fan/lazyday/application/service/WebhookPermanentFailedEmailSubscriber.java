package com.fan.lazyday.application.service;

import com.fan.lazyday.domain.event.WebhookPermanentFailedEvent;
import com.fan.lazyday.domain.tenant.repository.TenantRepository;
import com.fan.lazyday.domain.user.repository.UserRepository;
import com.fan.lazyday.domain.webhookconfig.repository.WebhookConfigRepository;
import com.fan.lazyday.infrastructure.event.DomainEventDeduplicator;
import com.fan.lazyday.infrastructure.event.DomainEventPublisher;
import com.fan.lazyday.infrastructure.properties.ServiceProperties;
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
public class WebhookPermanentFailedEmailSubscriber {

    private final DomainEventPublisher domainEventPublisher;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final WebhookConfigRepository webhookConfigRepository;
    private final DomainEventDeduplicator domainEventDeduplicator;
    private final ServiceProperties serviceProperties;
    private Disposable subscription;

    @PostConstruct
    void subscribe() {
        subscription = domainEventPublisher.asFlux()
                .ofType(WebhookPermanentFailedEvent.class)
                .flatMap(this::handle)
                .subscribe(
                        ignored -> {
                        },
                        error -> log.error("Webhook permanent failed email subscriber terminated unexpectedly", error)
                );
    }

    @PreDestroy
    void dispose() {
        if (subscription != null) {
            subscription.dispose();
        }
    }

    Mono<Void> handle(WebhookPermanentFailedEvent event) {
        String dedupKey = "email:webhook-permanent-failed:" + event.tenantId() + ":" + event.configId();
        if (!domainEventDeduplicator.tryRecord(dedupKey)) {
            return Mono.empty();
        }
        return userRepository.findTenantAdminEmailsByTenantId(event.tenantId())
                .collectList()
                .flatMap(recipients -> {
                    if (recipients.isEmpty()) {
                        log.warn("webhook permanent failed email skipped - no admin found: tenantId={}, configId={}",
                                event.tenantId(), event.configId());
                        return Mono.empty();
                    }
                    return Mono.zip(
                                    tenantRepository.findById(event.tenantId()),
                                    webhookConfigRepository.findByIdAndTenantId(event.configId(), event.tenantId())
                            )
                            .flatMap(tuple -> {
                                String webhookName = tuple.getT2().getName();
                                return emailService.send(
                                        recipients,
                                        "Webhook 推送已永久失败 - " + webhookName,
                                        "webhook-permanent-failed",
                                        Map.of(
                                                "tenantName", tuple.getT1().getName(),
                                                "webhookName", webhookName,
                                                "webhookUrl", tuple.getT2().getUrl(),
                                                "eventType", event.failedEventType(),
                                                "eventId", event.eventId(),
                                                "lastHttpStatus", event.lastHttpStatus() == null ? "" : event.lastHttpStatus(),
                                                "lastError", event.lastError() == null ? "" : event.lastError(),
                                                "webhookConfigPortalUrl", buildPortalUrl("/webhooks?id=" + event.configId())
                                        )
                                );
                            });
                })
                .onErrorResume(ex -> {
                    log.warn("webhook permanent failed email failed: tenantId={}, configId={}",
                            event.tenantId(), event.configId(), ex);
                    return Mono.empty();
                });
    }

    private String buildPortalUrl(String path) {
        String host = serviceProperties.getDomainHost();
        String prefix = serviceProperties.getPortalContextPathV1();
        return (host == null ? "" : host) + (prefix == null ? "" : prefix) + path;
    }
}
