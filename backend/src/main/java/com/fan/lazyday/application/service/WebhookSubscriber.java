package com.fan.lazyday.application.service;

import com.fan.lazyday.domain.event.DomainEvent;
import com.fan.lazyday.domain.webhookconfig.po.WebhookConfigPO;
import com.fan.lazyday.domain.webhookconfig.repository.WebhookConfigRepository;
import com.fan.lazyday.domain.webhookevent.po.WebhookEventPO;
import com.fan.lazyday.domain.webhookevent.repository.WebhookEventRepository;
import com.fan.lazyday.infrastructure.event.DomainEventPublisher;
import com.fan.lazyday.infrastructure.utils.JsonUtils;
import com.fan.lazyday.infrastructure.utils.id.SnowflakeIdWorker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookSubscriber {

    private final DomainEventPublisher domainEventPublisher;
    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final SnowflakeIdWorker snowflakeIdWorker;
    private Disposable subscription;

    @PostConstruct
    void subscribe() {
        subscription = domainEventPublisher.asFlux()
                .flatMap(this::materialize)
                .subscribe(
                        ignored -> {
                        },
                        error -> log.error("Webhook subscriber stream terminated unexpectedly", error)
                );
    }

    @PreDestroy
    void dispose() {
        if (subscription != null) {
            subscription.dispose();
        }
    }

    Mono<Void> materialize(DomainEvent event) {
        return webhookConfigRepository.findActiveByTenantIdAndEventType(event.tenantId(), event.eventType())
                .flatMap(config -> webhookEventRepository.insert(toWebhookEvent(config, event)))
                .then()
                .onErrorResume(ex -> {
                    log.error("Failed to materialize webhook event: tenantId={}, eventType={}",
                            event.tenantId(), event.eventType(), ex);
                    return Mono.empty();
                });
    }

    private WebhookEventPO toWebhookEvent(WebhookConfigPO config, DomainEvent event) {
        Instant now = Instant.now();
        return new WebhookEventPO()
                .setId(snowflakeIdWorker.nextId())
                .setTenantId(event.tenantId())
                .setConfigId(config.getId())
                .setEventType(event.eventType())
                .setPayload(toPayload(event))
                .setStatus("pending")
                .setRetryCount(0)
                .setNextRetryAt(now)
                .setCreatedTime(now);
    }

    private String toPayload(DomainEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (event instanceof com.fan.lazyday.domain.event.AppKeyDisabledEvent appKeyDisabled) {
            data.put("app_key_id", appKeyDisabled.appKeyId());
            data.put("app_key_value", appKeyDisabled.appKeyValue());
            data.put("disabled_time", appKeyDisabled.disabledTime().toString());
        } else if (event instanceof com.fan.lazyday.domain.event.AppKeyRotatedEvent appKeyRotated) {
            data.put("app_key_id", appKeyRotated.appKeyId());
            data.put("rotated_time", appKeyRotated.rotatedTime().toString());
            data.put("previous_secret_grace_until", appKeyRotated.previousSecretGraceUntil().toString());
        } else if (event instanceof com.fan.lazyday.domain.event.QuotaExceededEvent quotaExceeded) {
            data.put("period", quotaExceeded.period());
            data.put("limit", quotaExceeded.limit());
        } else if (event instanceof com.fan.lazyday.domain.event.QuotaPlanChangedEvent planChanged) {
            data.put("previous_plan_id", planChanged.previousPlanId());
            data.put("new_plan_id", planChanged.newPlanId());
            data.put("change_time", planChanged.changeTime().toString());
        } else if (event instanceof com.fan.lazyday.domain.event.WebhookPermanentFailedEvent permanentFailed) {
            data.put("event_id", permanentFailed.eventId());
            data.put("config_id", permanentFailed.configId());
            data.put("failed_event_type", permanentFailed.failedEventType());
            data.put("last_http_status", permanentFailed.lastHttpStatus());
            data.put("last_error", permanentFailed.lastError());
        }
        return JsonUtils.toJSONString(Map.of(
                "event_type", event.eventType(),
                "event_time", event.eventTime().toString(),
                "tenant_id", event.tenantId(),
                "data", data
        ));
    }
}
