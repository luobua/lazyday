package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.application.facade.QuotaFacade;
import com.fan.lazyday.infrastructure.config.path.RequestMappingAdminV1;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.interfaces.api.AdminPlanApi;
import com.fan.lazyday.interfaces.request.CreatePlanRequest;
import com.fan.lazyday.interfaces.request.UpdatePlanRequest;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.QuotaPlanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMappingAdminV1
@RequiredArgsConstructor
public class AdminPlanHandler implements AdminPlanApi {

    private final QuotaFacade quotaFacade;

    @Override
    public Mono<ApiResponse<List<QuotaPlanResponse>>> listPlans() {
        return quotaFacade.listPlans()
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<QuotaPlanResponse>> createPlan(Mono<CreatePlanRequest> request) {
        return request.flatMap(quotaFacade::createPlan)
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<QuotaPlanResponse>> updatePlan(Long id, Mono<UpdatePlanRequest> request) {
        return request.flatMap(req -> quotaFacade.updatePlan(id, req))
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<QuotaPlanResponse>> deletePlan(Long id) {
        return quotaFacade.disablePlan(id)
                .flatMap(this::wrapSuccess);
    }

    private <T> Mono<ApiResponse<T>> wrapSuccess(T data) {
        return RequestIdFilter.getRequestId()
                .defaultIfEmpty("unknown")
                .map(requestId -> ApiResponse.success(data, requestId));
    }
}
