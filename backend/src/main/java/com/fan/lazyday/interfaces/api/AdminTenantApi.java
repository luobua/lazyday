package com.fan.lazyday.interfaces.api;

import com.fan.lazyday.interfaces.response.AdminOverviewMetricsResponse;
import com.fan.lazyday.interfaces.response.AdminTenantDetailResponse;
import com.fan.lazyday.interfaces.response.AdminTenantSummaryResponse;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.PageResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

public interface AdminTenantApi {

    @GetMapping("/tenants")
    Mono<ApiResponse<PageResponse<AdminTenantSummaryResponse>>> listTenants(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    );

    @GetMapping("/tenants/{id}")
    Mono<ApiResponse<AdminTenantDetailResponse>> getTenantDetail(@PathVariable Long id);

    @PostMapping("/tenants/{id}/suspend")
    Mono<ApiResponse<AdminTenantSummaryResponse>> suspendTenant(@PathVariable Long id);

    @PostMapping("/tenants/{id}/resume")
    Mono<ApiResponse<AdminTenantSummaryResponse>> resumeTenant(@PathVariable Long id);

    @GetMapping("/overview")
    Mono<ApiResponse<AdminOverviewMetricsResponse>> getOverview();
}
