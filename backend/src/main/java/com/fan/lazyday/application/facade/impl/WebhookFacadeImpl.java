package com.fan.lazyday.application.facade.impl;

import com.fan.lazyday.application.facade.WebhookFacade;
import com.fan.lazyday.domain.appkey.entity.AppKeyEntity;
import com.fan.lazyday.domain.webhookconfig.WebhookConfigAggregation;
import com.fan.lazyday.domain.webhookconfig.WebhookEventType;
import com.fan.lazyday.domain.webhookconfig.po.WebhookConfigPO;
import com.fan.lazyday.domain.webhookconfig.repository.WebhookConfigRepository;
import com.fan.lazyday.infrastructure.exception.BizException;
import com.fan.lazyday.infrastructure.properties.ServiceProperties;
import com.fan.lazyday.infrastructure.utils.JsonUtils;
import com.fan.lazyday.infrastructure.webhook.WebhookSigner;
import com.fan.lazyday.interfaces.request.CreateWebhookRequest;
import com.fan.lazyday.interfaces.request.UpdateWebhookRequest;
import com.fan.lazyday.interfaces.response.WebhookConfigResponse;
import com.fan.lazyday.interfaces.response.WebhookTestResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.relational.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebhookFacadeImpl implements WebhookFacade {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int SECRET_BYTES = 32;
    private static final int RESPONSE_EXCERPT_LIMIT = 1024;

    private final WebhookConfigRepository webhookConfigRepository;
    private final ServiceProperties serviceProperties;
    private final WebClient.Builder webClientBuilder;

    @Override
    public Mono<List<WebhookConfigResponse>> list(Long tenantId) {
        return webhookConfigRepository.findByTenantId(tenantId)
                .map(po -> toResponse(po, null))
                .collectList();
    }

    @Override
    public Mono<WebhookConfigResponse> get(Long tenantId, Long id) {
        return findScoped(tenantId, id).map(po -> toResponse(po, null));
    }

    @Override
    public Mono<WebhookConfigResponse> create(Long tenantId, CreateWebhookRequest request) {
        return Mono.fromCallable(() -> {
                    validateUrl(request.getUrl());
                    List<WebhookEventType> eventTypes = parseEventTypes(request.getEventTypes());
                    String secret = generateSecret();
                    String encryptedSecret = encryptSecret(secret);
                    WebhookConfigAggregation aggregation = WebhookConfigAggregation.create(
                            tenantId,
                            request.getName(),
                            request.getUrl(),
                            eventTypes,
                            encryptedSecret
                    );
                    return new CreatePrepared(aggregation.getWebhookConfigEntity().getDelegate(), secret);
                })
                .flatMap(prepared -> webhookConfigRepository.insert(prepared.po())
                        .map(saved -> toResponse(saved, prepared.secret())));
    }

    @Override
    public Mono<WebhookConfigResponse> update(Long tenantId, Long id, UpdateWebhookRequest request) {
        return findScoped(tenantId, id)
                .flatMap(existing -> Mono.fromCallable(() -> buildUpdate(id, request))
                        .flatMap(update -> webhookConfigRepository.updateByIdAndTenantId(id, tenantId, update)))
                .then(findScoped(tenantId, id))
                .map(po -> toResponse(po, null));
    }

    @Override
    public Mono<Void> delete(Long tenantId, Long id) {
        return findScoped(tenantId, id)
                .flatMap(existing -> webhookConfigRepository.updateByIdAndTenantId(
                        id,
                        tenantId,
                        Update.update("status", "DISABLED")
                ))
                .then();
    }

    @Override
    public Mono<WebhookConfigResponse> rotateSecret(Long tenantId, Long id) {
        return findScoped(tenantId, id)
                .flatMap(existing -> Mono.fromCallable(() -> generateSecret())
                        .flatMap(secret -> webhookConfigRepository.updateByIdAndTenantId(
                                        id,
                                        tenantId,
                                        Update.update("secret_encrypted", encryptSecret(secret))
                                )
                                .thenReturn(toResponse(existing, secret))));
    }

    @Override
    public Mono<WebhookTestResultResponse> testPush(Long tenantId, Long id) {
        return findScoped(tenantId, id)
                .flatMap(config -> {
                    long started = System.nanoTime();
                    String body = JsonUtils.toJSONString(Map.of(
                            "event_type", "webhook.test",
                            "event_time", Instant.now().toString(),
                            "tenant_id", tenantId,
                            "webhook_config_id", id
                    ));
                    String timestamp = String.valueOf(Instant.now().getEpochSecond());
                    String secret = decryptSecret(config.getSecretEncrypted());
                    return webClientBuilder.build()
                            .post()
                            .uri(config.getUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Lazyday-Event-Id", "test-" + id)
                            .header("X-Lazyday-Event-Type", "webhook.test")
                            .header("X-Lazyday-Timestamp", timestamp)
                            .header("X-Lazyday-Signature", WebhookSigner.sign(secret, timestamp, body))
                            .header("User-Agent", "lazyday-webhook/1.0")
                            .bodyValue(body)
                            .exchangeToMono(response -> response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(responseBody -> {
                                        WebhookTestResultResponse result = new WebhookTestResultResponse();
                                        result.setHttpStatus(response.statusCode().value());
                                        result.setResponseHeaders(topHeaders(response.headers().asHttpHeaders().toSingleValueMap()));
                                        result.setResponseBodyExcerpt(truncate(responseBody, RESPONSE_EXCERPT_LIMIT));
                                        result.setLatencyMs(elapsedMs(started));
                                        return result;
                                    }))
                            .timeout(Duration.ofMillis(serviceProperties.getWebhook().getHttpTimeoutMs()))
                            .onErrorResume(ex -> Mono.just(errorResult(ex, elapsedMs(started))));
                });
    }

    private Mono<WebhookConfigPO> findScoped(Long tenantId, Long id) {
        return webhookConfigRepository.findByIdAndTenantId(id, tenantId)
                .switchIfEmpty(Mono.error(BizException.notFound("WEBHOOK_NOT_FOUND", "Webhook 不存在")));
    }

    private Update buildUpdate(Long id, UpdateWebhookRequest request) {
        Update update = Update.update("id", id);
        if (request.getName() != null) {
            update = update.set("name", request.getName());
        }
        if (request.getUrl() != null) {
            validateUrl(request.getUrl());
            update = update.set("url", request.getUrl());
        }
        if (request.getEventTypes() != null) {
            update = update.set("event_types", WebhookConfigAggregation.serializeEventTypes(parseEventTypes(request.getEventTypes())));
        }
        if (request.getStatus() != null) {
            update = update.set("status", validateStatus(request.getStatus()));
        }
        return update;
    }

    private List<WebhookEventType> parseEventTypes(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw BizException.badRequest("WEBHOOK_EVENT_TYPES_EMPTY", "Webhook 事件类型不能为空");
        }
        return values.stream()
                .map(WebhookEventType::fromValue)
                .toList();
    }

    private String validateStatus(String status) {
        String normalized = status.toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(normalized) && !"DISABLED".equals(normalized)) {
            throw BizException.badRequest("WEBHOOK_INVALID_STATUS", "Webhook 状态无效");
        }
        return normalized;
    }

    private void validateUrl(String rawUrl) {
        URI uri = URI.create(rawUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw BizException.badRequest("WEBHOOK_INSECURE_URL", "Webhook URL 必须使用 HTTPS");
        }
        String host = uri.getHost();
        if (host == null || isPrivateHost(host)) {
            throw BizException.badRequest("WEBHOOK_PRIVATE_NETWORK_URL", "Webhook URL 不能指向本机或私有网络");
        }
    }

    private boolean isPrivateHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalized) || normalized.endsWith(".localhost")) {
            return true;
        }
        if (normalized.startsWith("127.") || normalized.startsWith("10.") || normalized.startsWith("169.254.")) {
            return true;
        }
        if (normalized.startsWith("192.168.")) {
            return true;
        }
        if (normalized.startsWith("172.")) {
            String[] parts = normalized.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private WebhookConfigResponse toResponse(WebhookConfigPO po, String plainSecret) {
        WebhookConfigResponse response = new WebhookConfigResponse();
        response.setId(po.getId());
        response.setName(po.getName());
        response.setUrl(po.getUrl());
        response.setEventTypes(parseStoredEventTypes(po.getEventTypes()));
        response.setStatus(po.getStatus());
        response.setCreateTime(po.getCreateTime());
        response.setUpdateTime(po.getUpdateTime());
        response.setSecret(plainSecret);
        return response;
    }

    private List<String> parseStoredEventTypes(String eventTypes) {
        if (eventTypes == null || eventTypes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(eventTypes.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String encryptSecret(String plainSecret) {
        return AppKeyEntity.encryptSecretKey(plainSecret, serviceProperties.getEncryptionKey());
    }

    private String decryptSecret(String encryptedSecret) {
        return AppKeyEntity.decryptSecretKey(encryptedSecret, serviceProperties.getEncryptionKey());
    }

    public static String encryptSecretForTest(String plainSecret, String encryptionKey) {
        return AppKeyEntity.encryptSecretKey(plainSecret, encryptionKey);
    }

    private static Map<String, String> topHeaders(Map<String, String> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        headers.entrySet().stream()
                .limit(10)
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private static String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    private static long elapsedMs(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private static WebhookTestResultResponse errorResult(Throwable ex, long latencyMs) {
        WebhookTestResultResponse result = new WebhookTestResultResponse();
        result.setLatencyMs(latencyMs);
        result.setErrorCode(ex instanceof java.util.concurrent.TimeoutException ? "WEBHOOK_TEST_TIMEOUT" : "WEBHOOK_TEST_FAILED");
        result.setError(ex.getMessage());
        return result;
    }

    private record CreatePrepared(WebhookConfigPO po, String secret) {
    }
}
