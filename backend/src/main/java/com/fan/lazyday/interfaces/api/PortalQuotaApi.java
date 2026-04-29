package com.fan.lazyday.interfaces.api;

import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.QuotaUsageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Mono;

public interface PortalQuotaApi {

    @GetMapping("/quota")
    Mono<ApiResponse<QuotaUsageResponse>> getQuotaUsage();
}
