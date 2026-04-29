package com.fan.lazyday.application.facade;

import com.fan.lazyday.interfaces.request.CreateWebhookRequest;
import com.fan.lazyday.interfaces.request.UpdateWebhookRequest;
import com.fan.lazyday.interfaces.response.WebhookConfigResponse;
import com.fan.lazyday.interfaces.response.WebhookTestResultResponse;
import reactor.core.publisher.Mono;

import java.util.List;

public interface WebhookFacade {
    Mono<List<WebhookConfigResponse>> list(Long tenantId);

    Mono<WebhookConfigResponse> get(Long tenantId, Long id);

    Mono<WebhookConfigResponse> create(Long tenantId, CreateWebhookRequest request);

    Mono<WebhookConfigResponse> update(Long tenantId, Long id, UpdateWebhookRequest request);

    Mono<Void> delete(Long tenantId, Long id);

    Mono<WebhookConfigResponse> rotateSecret(Long tenantId, Long id);

    Mono<WebhookTestResultResponse> testPush(Long tenantId, Long id);
}
