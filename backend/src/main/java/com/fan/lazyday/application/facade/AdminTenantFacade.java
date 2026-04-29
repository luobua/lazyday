package com.fan.lazyday.application.facade;

import com.fan.lazyday.interfaces.response.AdminOverviewMetricsResponse;
import com.fan.lazyday.interfaces.response.AdminTenantDetailResponse;
import com.fan.lazyday.interfaces.response.AdminTenantSummaryResponse;
import com.fan.lazyday.interfaces.response.PageResponse;
import reactor.core.publisher.Mono;

public interface AdminTenantFacade {
    Mono<PageResponse<AdminTenantSummaryResponse>> listTenants(String keyword, String status, int page, int size);

    Mono<AdminTenantDetailResponse> getTenantDetail(Long tenantId);

    Mono<AdminTenantSummaryResponse> suspendTenant(Long tenantId);

    Mono<AdminTenantSummaryResponse> resumeTenant(Long tenantId);

    Mono<AdminOverviewMetricsResponse> getOverview();
}
