package com.fan.lazyday.application.facade;

import com.fan.lazyday.interfaces.response.CallLogResponse;
import com.fan.lazyday.interfaces.response.CallLogStatsResponse;
import com.fan.lazyday.interfaces.response.PageResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface CallLogFacade {

    Mono<Void> recordAsync(com.fan.lazyday.domain.calllog.po.CallLog callLog);

    Mono<PageResponse<CallLogResponse>> queryPaged(Long tenantId, Instant from, Instant to,
                                                   Short statusCode, int page, int size);

    Mono<CallLogStatsResponse> aggregateByHour(Long tenantId, Instant from, Instant to);

    Mono<CallLogStatsResponse> aggregateByDay(Long tenantId, Instant from, Instant to);

    Mono<PageResponse<CallLogResponse>> queryLogs(Long tenantId, String appKey, String path,
                                                  Short statusCode, Integer statusCodeGroup,
                                                  Instant from, Instant to, int page, int size);

    Mono<CallLogStatsResponse> getStats(Long tenantId, Instant from, Instant to);

    Flux<CallLogResponse> exportLogs(Long tenantId, String appKey, String path,
                                     Short statusCode, Integer statusCodeGroup,
                                     Instant from, Instant to);
}
