package com.fan.lazyday.infrastructure.scheduler;

import com.fan.lazyday.domain.appkey.entity.AppKeyEntity;
import com.fan.lazyday.domain.event.WebhookPermanentFailedEvent;
import com.fan.lazyday.domain.webhookconfig.po.WebhookConfigPO;
import com.fan.lazyday.domain.webhookconfig.repository.WebhookConfigRepository;
import com.fan.lazyday.domain.webhookevent.po.WebhookEventPO;
import com.fan.lazyday.domain.webhookevent.repository.WebhookEventRepository;
import com.fan.lazyday.infrastructure.event.DomainEventPublisher;
import com.fan.lazyday.infrastructure.properties.ServiceProperties;
import com.fan.lazyday.infrastructure.webhook.WebhookSigner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookDispatcher {

    private static final int BATCH_SIZE = 100;
    private static final int RESPONSE_EXCERPT_LIMIT = 1024;
    private static final int ERROR_EXCERPT_LIMIT = 500;
    private static final String LOCKED_BY = instanceId();

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookConfigRepository webhookConfigRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final ServiceProperties serviceProperties;
    private final WebClient.Builder webClientBuilder;

    @Scheduled(fixedDelayString = "#{@serviceProperties.webhook.dispatchIntervalSeconds * 1000}")
    public void dispatchScheduled() {
        dispatchOnce()
                .doOnError(error -> log.error("Webhook dispatch failed", error))
                .subscribe();
    }

    Mono<Void> dispatchOnce() {
        if (!serviceProperties.getWebhook().isDispatchEnabled()) {
            return Mono.empty();
        }
        return webhookEventRepository.recoverGhostLocks()
                .thenMany(webhookEventRepository.claimDueForDispatch(BATCH_SIZE, LOCKED_BY))
                .flatMap(this::deliver)
                .then();
    }

    private Mono<Void> deliver(WebhookEventPO event) {
        return webhookConfigRepository.findByIdAndTenantId(event.getConfigId(), event.getTenantId())
                .flatMap(config -> doDeliver(event, config).thenReturn(true))
                .switchIfEmpty(Mono.defer(() -> handleFailure(event, null, null, "webhook config not found").thenReturn(false)))
                .then();
    }

    private Mono<Void> doDeliver(WebhookEventPO event, WebhookConfigPO config) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String secret = AppKeyEntity.decryptSecretKey(config.getSecretEncrypted(), serviceProperties.getEncryptionKey());
        return webClientBuilder.build()
                .post()
                .uri(config.getUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Lazyday-Event-Id", String.valueOf(event.getId()))
                .header("X-Lazyday-Event-Type", event.getEventType())
                .header("X-Lazyday-Timestamp", timestamp)
                .header("X-Lazyday-Signature", WebhookSigner.sign(secret, timestamp, event.getPayload()))
                .header("User-Agent", "lazyday-webhook/1.0")
                .bodyValue(event.getPayload())
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            int statusCode = response.statusCode().value();
                            if (response.statusCode().is2xxSuccessful()) {
                                return webhookEventRepository.updateToSucceeded(event.getId(), statusCode).then();
                            }
                            return handleFailure(event, statusCode, truncate(body, RESPONSE_EXCERPT_LIMIT), null);
                        }))
                .timeout(Duration.ofMillis(serviceProperties.getWebhook().getHttpTimeoutMs()))
                .onErrorResume(ex -> handleFailure(event, null, null, truncate(ex.getMessage(), ERROR_EXCERPT_LIMIT)));
    }

    private Mono<Void> handleFailure(WebhookEventPO event,
                                     Integer httpStatus,
                                     String responseExcerpt,
                                     String error) {
        int nextRetryCount = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
        if (nextRetryCount >= serviceProperties.getWebhook().getMaxRetries()) {
            return webhookEventRepository.updateToPermanentFailed(
                            event.getId(),
                            nextRetryCount,
                            httpStatus,
                            responseExcerpt,
                            error
                    )
                    .doOnSuccess(ignored -> domainEventPublisher.publish(new WebhookPermanentFailedEvent(
                            event.getTenantId(),
                            event.getId(),
                            event.getConfigId(),
                            event.getEventType(),
                            httpStatus,
                            error,
                            Instant.now()
                    )))
                    .then();
        }
        Instant nextRetryAt = Instant.now().plusSeconds(backoffSeconds(nextRetryCount));
        return webhookEventRepository.updateToFailedForRetry(
                        event.getId(),
                        nextRetryCount,
                        nextRetryAt,
                        httpStatus,
                        responseExcerpt,
                        error
                )
                .then();
    }

    private long backoffSeconds(int retryCount) {
        List<Long> sequence = java.util.Arrays.stream(serviceProperties.getWebhook().getBackoffSequence().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::parseLong)
                .toList();
        int index = Math.max(0, Math.min(retryCount - 1, sequence.size() - 1));
        return sequence.get(index);
    }

    private static String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    private static String instanceId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + java.util.UUID.randomUUID();
        } catch (Exception ex) {
            return "lazyday-" + java.util.UUID.randomUUID();
        }
    }
}
