package com.fan.lazyday.interfaces.api;

import com.fan.lazyday.interfaces.request.CreateWebhookRequest;
import com.fan.lazyday.interfaces.request.UpdateWebhookRequest;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.WebhookConfigResponse;
import com.fan.lazyday.interfaces.response.WebhookTestResultResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PortalWebhookApi {

    @GetMapping("/webhooks")
    Mono<ApiResponse<List<WebhookConfigResponse>>> list();

    @GetMapping("/webhooks/{id}")
    Mono<ApiResponse<WebhookConfigResponse>> get(@PathVariable Long id);

    @PostMapping("/webhooks")
    Mono<ApiResponse<WebhookConfigResponse>> create(@RequestBody @Validated Mono<CreateWebhookRequest> request);

    @PutMapping("/webhooks/{id}")
    Mono<ApiResponse<WebhookConfigResponse>> update(@PathVariable Long id,
                                                    @RequestBody @Validated Mono<UpdateWebhookRequest> request);

    @DeleteMapping("/webhooks/{id}")
    Mono<ApiResponse<Void>> delete(@PathVariable Long id);

    @PostMapping("/webhooks/{id}/rotate-secret")
    Mono<ApiResponse<WebhookConfigResponse>> rotateSecret(@PathVariable Long id);

    @PostMapping("/webhooks/{id}/test")
    Mono<ApiResponse<WebhookTestResultResponse>> test(@PathVariable Long id);
}
