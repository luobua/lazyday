package com.fan.lazyday.application.facade.impl;

import com.fan.lazyday.application.facade.CallLogFacade;
import com.fan.lazyday.domain.calllog.po.CallLog;
import com.fan.lazyday.domain.calllog.repository.CallLogRepository;
import com.fan.lazyday.infrastructure.exception.ErrorCode;
import com.fan.lazyday.interfaces.response.CallLogResponse;
import com.fan.lazyday.interfaces.response.CallLogStatsResponse;
import com.fan.lazyday.interfaces.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallLogFacadeImpl implements CallLogFacade {

    private final CallLogRepository callLogRepository;

    @Override
    public Mono<Void> recordAsync(CallLog callLog) {
        callLogRepository.insert(callLog)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(error -> logFailure(error))
                .onErrorResume(error -> Mono.empty())
                .subscribe();
        return Mono.empty();
    }

    @Override
    public Mono<PageResponse<CallLogResponse>> queryPaged(Long tenantId, Instant from, Instant to,
                                                          Short statusCode, int page, int size) {
        return callLogRepository.findByTenantIdPaged(tenantId, from, to, statusCode, page, size)
                .map(this::toPageResponse);
    }

    @Override
    public Mono<CallLogStatsResponse> aggregateByHour(Long tenantId, Instant from, Instant to) {
        return buildStatsResponse(
                tenantId,
                from,
                to,
                "hour",
                callLogRepository.aggregateByHour(tenantId, from, to)
        );
    }

    @Override
    public Mono<CallLogStatsResponse> aggregateByDay(Long tenantId, Instant from, Instant to) {
        return buildStatsResponse(
                tenantId,
                from,
                to,
                "day",
                callLogRepository.aggregateByDay(tenantId, from, to)
        );
    }

    @Override
    public Mono<PageResponse<CallLogResponse>> queryLogs(Long tenantId, String appKey, String path,
                                                         Short statusCode, Integer statusCodeGroup,
                                                         Instant from, Instant to, int page, int size) {
        return callLogRepository.findByTenantId(tenantId, appKey, path, statusCode, statusCodeGroup, from, to, page, size)
                .map(this::toPageResponse);
    }

    @Override
    public Mono<CallLogStatsResponse> getStats(Long tenantId, Instant from, Instant to) {
        return aggregateByDay(tenantId, from, to);
    }

    @Override
    public Flux<CallLogResponse> exportLogs(Long tenantId, String appKey, String path,
                                            Short statusCode, Integer statusCodeGroup,
                                            Instant from, Instant to) {
        return callLogRepository.findAllByTenantId(tenantId, appKey, path, statusCode, statusCodeGroup, from, to)
                .map(this::toResponse);
    }

    private Mono<CallLogStatsResponse> buildStatsResponse(Long tenantId, Instant from, Instant to,
                                                          String granularity,
                                                          Flux<CallLogRepository.TimeBucketStats> bucketFlux) {
        return Mono.zip(
                        callLogRepository.getStats(tenantId, from, to),
                        bucketFlux.collectList()
                )
                .map(tuple -> {
                    CallLogRepository.CallLogStats stats = tuple.getT1();
                    CallLogStatsResponse response = new CallLogStatsResponse();
                    response.setGranularity(granularity);
                    response.setTotal(stats.total());
                    response.setSuccessCount(stats.successCount());
                    response.setClientErrorCount(stats.clientErrorCount());
                    response.setServerErrorCount(stats.serverErrorCount());
                    response.setAvgLatencyMs(stats.avgLatencyMs());
                    response.setBuckets(tuple.getT2().stream().map(this::toBucketItem).toList());
                    return response;
                });
    }

    private PageResponse<CallLogResponse> toPageResponse(org.springframework.data.domain.Page<CallLog> pageResult) {
        return PageResponse.of(
                pageResult.getContent().stream().map(this::toResponse).toList(),
                pageResult.getTotalElements(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalPages()
        );
    }

    private CallLogStatsResponse.BucketItem toBucketItem(CallLogRepository.TimeBucketStats item) {
        CallLogStatsResponse.BucketItem response = new CallLogStatsResponse.BucketItem();
        response.setBucketTime(item.bucketTime());
        response.setTotalCount(item.totalCount());
        response.setSuccessCount(item.successCount());
        response.setErrorCount(item.errorCount());
        return response;
    }

    private CallLogResponse toResponse(CallLog callLog) {
        CallLogResponse response = new CallLogResponse();
        response.setId(callLog.getId());
        response.setAppKey(maskKey(callLog.getAppKey()));
        response.setPath(callLog.getPath());
        response.setMethod(callLog.getMethod());
        response.setStatusCode(callLog.getStatusCode());
        response.setLatencyMs(callLog.getLatencyMs());
        response.setClientIp(callLog.getClientIp());
        response.setRequestTime(callLog.getRequestTime());
        return response;
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 8) return key;
        return key.substring(0, 7) + "****" + key.substring(key.length() - 4);
    }

    private void logFailure(Throwable error) {
        if (isPartitionMissing(error)) {
            log.error("{}: failed to persist call log asynchronously", ErrorCode.PARTITION_MISSING.getCode(), error);
            return;
        }
        log.warn("Failed to persist call log asynchronously", error);
    }

    private boolean isPartitionMissing(Throwable error) {
        String message = error.getMessage();
        return message != null && message.contains("no partition of relation");
    }
}
