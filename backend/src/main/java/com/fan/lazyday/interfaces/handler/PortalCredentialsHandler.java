package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.application.facade.CredentialsFacade;
import com.fan.lazyday.infrastructure.config.path.RequestMappingPortalV1;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.infrastructure.security.TenantContext;
import com.fan.lazyday.interfaces.api.PortalCredentialsApi;
import com.fan.lazyday.interfaces.request.CreateAppKeyRequest;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.AppKeyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMappingPortalV1
@RequiredArgsConstructor
public class PortalCredentialsHandler implements PortalCredentialsApi {

    private final CredentialsFacade credentialsFacade;

    @Override
    public Mono<ApiResponse<List<AppKeyResponse>>> list() {
        return TenantContext.current()
                .flatMap(ctx -> credentialsFacade.list(ctx.getTenantId()))
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<AppKeyResponse>> create(Mono<CreateAppKeyRequest> request) {
        return TenantContext.current()
                .flatMap(ctx -> request.flatMap(req ->
                        credentialsFacade.create(ctx.getTenantId(), req.getName(), req.getScopes())))
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<Void>> disable(Long id) {
        return TenantContext.current()
                .flatMap(ctx -> credentialsFacade.disable(ctx.getTenantId(), id))
                .then(wrapSuccess(null));
    }

    @Override
    public Mono<ApiResponse<Void>> enable(Long id) {
        return TenantContext.current()
                .flatMap(ctx -> credentialsFacade.enable(ctx.getTenantId(), id))
                .then(wrapSuccess(null));
    }

    @Override
    public Mono<ApiResponse<AppKeyResponse>> rotateSecret(Long id) {
        return TenantContext.current()
                .flatMap(ctx -> credentialsFacade.rotateSecret(ctx.getTenantId(), id))
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<Void>> delete(Long id) {
        return TenantContext.current()
                .flatMap(ctx -> credentialsFacade.delete(ctx.getTenantId(), id))
                .then(wrapSuccess(null));
    }

    private <T> Mono<ApiResponse<T>> wrapSuccess(T data) {
        return RequestIdFilter.getRequestId()
                .defaultIfEmpty("unknown")
                .map(requestId -> ApiResponse.success(data, requestId));
    }
}
