package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.application.facade.QuotaFacade;
import com.fan.lazyday.infrastructure.config.path.RequestMappingInternal;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.interfaces.api.InternalQuotaApi;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.EffectiveQuotaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMappingInternal
@RequiredArgsConstructor
public class InternalQuotaHandler implements InternalQuotaApi {

    private final QuotaFacade quotaFacade;

    @Override
    public Mono<ApiResponse<EffectiveQuotaResponse>> getEffectiveQuota(Long tenantId) {
        return quotaFacade.getEffectiveQuota(tenantId)
                .flatMap(this::wrapSuccess);
    }

    private <T> Mono<ApiResponse<T>> wrapSuccess(T data) {
        return RequestIdFilter.getRequestId()
                .defaultIfEmpty("unknown")
                .map(requestId -> ApiResponse.success(data, requestId));
    }
}
