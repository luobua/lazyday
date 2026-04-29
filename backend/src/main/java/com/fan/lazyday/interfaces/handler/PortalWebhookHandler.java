package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.application.facade.WebhookFacade;
import com.fan.lazyday.infrastructure.config.path.RequestMappingPortalV1;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.infrastructure.security.TenantContext;
import com.fan.lazyday.interfaces.api.PortalWebhookApi;
import com.fan.lazyday.interfaces.request.CreateWebhookRequest;
import com.fan.lazyday.interfaces.request.UpdateWebhookRequest;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.WebhookConfigResponse;
import com.fan.lazyday.interfaces.response.WebhookTestResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMappingPortalV1
@RequiredArgsConstructor
public class PortalWebhookHandler implements PortalWebhookApi {

    private final WebhookFacade webhookFacade;

    @Override
    public Mono<ApiResponse<List<WebhookConfigResponse>>> list() {
        return TenantContext.current()
                .flatMap(ctx -> webhookFacade.list(ctx.getTenantId()))
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<WebhookConfigResponse>> get(Long id) {
        return TenantContext.current()
                .flatMap(ctx -> webhookFacade.get(ctx.getTenantId(), id))
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<WebhookConfigResponse>> create(Mono<CreateWebhookRequest> request) {
        return TenantContext.current()
                .flatMap(ctx -> request.flatMap(req -> webhookFacade.create(ctx.getTenantId(), req)))
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<WebhookConfigResponse>> update(Long id, Mono<UpdateWebhookRequest> request) {
        return TenantContext.current()
                .flatMap(ctx -> request.flatMap(req -> webhookFacade.update(ctx.getTenantId(), id, req)))
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<Void>> delete(Long id) {
        return TenantContext.current()
                .flatMap(ctx -> webhookFacade.delete(ctx.getTenantId(), id))
                .then(wrapSuccess(null));
    }

    @Override
    public Mono<ApiResponse<WebhookConfigResponse>> rotateSecret(Long id) {
        return TenantContext.current()
                .flatMap(ctx -> webhookFacade.rotateSecret(ctx.getTenantId(), id))
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<WebhookTestResultResponse>> test(Long id) {
        return TenantContext.current()
                .flatMap(ctx -> webhookFacade.testPush(ctx.getTenantId(), id))
                .flatMap(this::wrapSuccess);
    }

    private <T> Mono<ApiResponse<T>> wrapSuccess(T data) {
        return RequestIdFilter.getRequestId()
                .defaultIfEmpty("unknown")
                .map(requestId -> ApiResponse.success(data, requestId));
    }
}
