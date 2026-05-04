package com.fan.lazyday.interfaces.api.internal;

import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.BrainDispatchLogResponse;
import com.fan.lazyday.interfaces.response.EdgeStatusResponse;
import com.fan.lazyday.interfaces.response.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface InternalDispatchApi {

    @PostMapping("/dispatch/{tenantId}")
    Mono<ApiResponse<Map<String, String>>> dispatch(@PathVariable Long tenantId,
                                                    @RequestBody Mono<String> payload);

    @GetMapping("/dispatch/logs")
    Mono<ApiResponse<PageResponse<BrainDispatchLogResponse>>> listLogs(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(name = "status", required = false) List<String> statuses,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String msgId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    @GetMapping("/dispatch/logs/{msgId}")
    Mono<ApiResponse<BrainDispatchLogResponse>> getLog(@PathVariable String msgId);

    @GetMapping("/edge/status")
    Mono<ApiResponse<EdgeStatusResponse>> getEdgeStatus();
}
