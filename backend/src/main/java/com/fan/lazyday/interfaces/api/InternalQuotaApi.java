package com.fan.lazyday.interfaces.api;

import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.EffectiveQuotaResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

public interface InternalQuotaApi {

    @GetMapping("/quota/effective")
    Mono<ApiResponse<EffectiveQuotaResponse>> getEffectiveQuota(@RequestParam Long tenantId);
}
