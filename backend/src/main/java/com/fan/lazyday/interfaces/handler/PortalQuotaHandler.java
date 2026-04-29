package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.application.facade.QuotaFacade;
import com.fan.lazyday.infrastructure.config.path.RequestMappingPortalV1;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.infrastructure.security.TenantContext;
import com.fan.lazyday.interfaces.api.PortalQuotaApi;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.QuotaUsageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMappingPortalV1
@RequiredArgsConstructor
public class PortalQuotaHandler implements PortalQuotaApi {

    private final QuotaFacade quotaFacade;

    @Override
    public Mono<ApiResponse<QuotaUsageResponse>> getQuotaUsage() {
        return TenantContext.current()
                .flatMap(ctx -> quotaFacade.getQuotaUsage(ctx.getTenantId()))
                .flatMap(this::wrapSuccess);
    }

    private <T> Mono<ApiResponse<T>> wrapSuccess(T data) {
        return RequestIdFilter.getRequestId()
                .defaultIfEmpty("unknown")
                .map(requestId -> ApiResponse.success(data, requestId));
    }
}
