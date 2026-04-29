package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.application.facade.AdminTenantFacade;
import com.fan.lazyday.infrastructure.config.path.RequestMappingAdminV1;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.interfaces.api.AdminTenantApi;
import com.fan.lazyday.interfaces.response.AdminOverviewMetricsResponse;
import com.fan.lazyday.interfaces.response.AdminTenantDetailResponse;
import com.fan.lazyday.interfaces.response.AdminTenantSummaryResponse;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMappingAdminV1
@RequiredArgsConstructor
public class AdminTenantHandler implements AdminTenantApi {

    private final AdminTenantFacade adminTenantFacade;

    @Override
    public Mono<ApiResponse<PageResponse<AdminTenantSummaryResponse>>> listTenants(String keyword, String status, int page, int size) {
        return adminTenantFacade.listTenants(keyword, status, page, size)
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<AdminTenantDetailResponse>> getTenantDetail(Long id) {
        return adminTenantFacade.getTenantDetail(id)
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<AdminTenantSummaryResponse>> suspendTenant(Long id) {
        return adminTenantFacade.suspendTenant(id)
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<AdminTenantSummaryResponse>> resumeTenant(Long id) {
        return adminTenantFacade.resumeTenant(id)
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<AdminOverviewMetricsResponse>> getOverview() {
        return adminTenantFacade.getOverview()
                .flatMap(this::wrapSuccess);
    }

    private <T> Mono<ApiResponse<T>> wrapSuccess(T data) {
        return RequestIdFilter.getRequestId()
                .defaultIfEmpty("unknown")
                .map(requestId -> ApiResponse.success(data, requestId));
    }
}
