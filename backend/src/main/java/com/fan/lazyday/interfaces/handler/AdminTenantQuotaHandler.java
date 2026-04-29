package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.application.facade.QuotaFacade;
import com.fan.lazyday.infrastructure.config.path.RequestMappingAdminV1;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.interfaces.api.AdminTenantQuotaApi;
import com.fan.lazyday.interfaces.request.OverrideQuotaRequest;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.EffectiveQuotaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMappingAdminV1
@RequiredArgsConstructor
public class AdminTenantQuotaHandler implements AdminTenantQuotaApi {

    private final QuotaFacade quotaFacade;

    @Override
    public Mono<ApiResponse<EffectiveQuotaResponse>> overrideTenantQuota(Long tenantId, Mono<OverrideQuotaRequest> request) {
        return request.flatMap(req -> quotaFacade.overrideTenantQuota(tenantId, req))
                .flatMap(this::wrapSuccess);
    }

    private <T> Mono<ApiResponse<T>> wrapSuccess(T data) {
        return RequestIdFilter.getRequestId()
                .defaultIfEmpty("unknown")
                .map(requestId -> ApiResponse.success(data, requestId));
    }
}
