package com.fan.lazyday.interfaces.api;

import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.CallLogResponse;
import com.fan.lazyday.interfaces.response.CallLogStatsResponse;
import com.fan.lazyday.interfaces.response.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface PortalLogApi {

    @GetMapping("/logs")
    Mono<ApiResponse<PageResponse<CallLogResponse>>> listLogs(
            @RequestParam(name = "appKey", required = false) String appKey,
            @RequestParam(required = false) String path,
            @RequestParam(name = "statusCode", required = false) Short statusCode,
            @RequestParam(name = "statusCodeGroup", required = false) Integer statusCodeGroup,
            @RequestParam(name = "startTime", required = false) Instant from,
            @RequestParam(name = "endTime", required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    @GetMapping("/logs/stats")
    Mono<ApiResponse<CallLogStatsResponse>> getStats(
            @RequestParam(name = "startTime", required = false) Instant from,
            @RequestParam(name = "endTime", required = false) Instant to,
            @RequestParam(required = false) String granularity);

    @GetMapping("/logs/export")
    Mono<Void> exportCsv(
            @RequestParam(name = "appKey", required = false) String appKey,
            @RequestParam(required = false) String path,
            @RequestParam(name = "statusCode", required = false) Short statusCode,
            @RequestParam(name = "statusCodeGroup", required = false) Integer statusCodeGroup,
            @RequestParam(name = "startTime", required = false) Instant from,
            @RequestParam(name = "endTime", required = false) Instant to,
            org.springframework.web.server.ServerWebExchange exchange);
}
