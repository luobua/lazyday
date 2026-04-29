package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.application.facade.CallLogFacade;
import com.fan.lazyday.infrastructure.config.path.RequestMappingPortalV1;
import com.fan.lazyday.infrastructure.exception.BizException;
import com.fan.lazyday.infrastructure.exception.ErrorCode;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.infrastructure.security.TenantContext;
import com.fan.lazyday.interfaces.api.PortalLogApi;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.CallLogResponse;
import com.fan.lazyday.interfaces.response.CallLogStatsResponse;
import com.fan.lazyday.interfaces.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMappingPortalV1
@RequiredArgsConstructor
public class PortalLogHandler implements PortalLogApi {

    private final CallLogFacade callLogFacade;

    @Override
    public Mono<ApiResponse<PageResponse<CallLogResponse>>> listLogs(String appKey, String path,
                                                                     Short statusCode, Integer statusCodeGroup,
                                                                     Instant from, Instant to,
                                                                     int page, int size) {
        if (from == null || to == null) {
            return Mono.error(BizException.of(ErrorCode.INVALID_PARAMETER, "startTime 和 endTime 为必填参数"));
        }

        return TenantContext.current()
                .flatMap(ctx -> callLogFacade.queryLogs(ctx.getTenantId(), appKey, path,
                        statusCode, statusCodeGroup, from, to, page, size))
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<CallLogStatsResponse>> getStats(Instant from, Instant to, String granularity) {
        if (from == null || to == null) {
            return Mono.error(BizException.of(ErrorCode.INVALID_PARAMETER, "startTime 和 endTime 为必填参数"));
        }

        return TenantContext.current()
                .flatMap(ctx -> switch (normalizeGranularity(granularity)) {
                    case "hour" -> callLogFacade.aggregateByHour(ctx.getTenantId(), from, to);
                    case "day" -> callLogFacade.aggregateByDay(ctx.getTenantId(), from, to);
                    default -> Mono.error(BizException.of(
                            ErrorCode.INVALID_PARAMETER,
                            "granularity 仅支持 hour 或 day"
                    ));
                })
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<Void> exportCsv(String appKey, String path, Short statusCode, Integer statusCodeGroup,
                                 Instant from, Instant to, ServerWebExchange exchange) {
        return TenantContext.current()
                .flatMap(ctx -> {
                    Instant effectiveFrom = from != null ? from : defaultFrom();
                    Instant effectiveTo = to != null ? to : Instant.now();

                    exchange.getResponse().getHeaders().setContentType(MediaType.parseMediaType("text/csv"));
                    exchange.getResponse().getHeaders().set("Content-Disposition",
                            "attachment; filename=call_logs.csv");

                    String header = "id,app_key,path,method,status_code,latency_ms,client_ip,request_time\n";
                    DataBuffer headerBuf = exchange.getResponse().bufferFactory()
                            .wrap(header.getBytes(StandardCharsets.UTF_8));

                    return exchange.getResponse().writeWith(
                            callLogFacade.exportLogs(ctx.getTenantId(), appKey, path,
                                            statusCode, statusCodeGroup, effectiveFrom, effectiveTo)
                                    .map(log -> {
                                        String line = String.format("%d,%s,%s,%s,%d,%d,%s,%s\n",
                                                log.getId(), log.getAppKey(), log.getPath(),
                                                log.getMethod(), log.getStatusCode(), log.getLatencyMs(),
                                                log.getClientIp(), log.getRequestTime());
                                        return exchange.getResponse().bufferFactory()
                                                .wrap(line.getBytes(StandardCharsets.UTF_8));
                                    })
                                    .startWith(headerBuf)
                    );
                });
    }

    private Instant defaultFrom() {
        return LocalDate.now(ZoneOffset.UTC).minusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private String normalizeGranularity(String granularity) {
        return granularity == null || granularity.isBlank() ? "day" : granularity.trim().toLowerCase();
    }

    private <T> Mono<ApiResponse<T>> wrapSuccess(T data) {
        return RequestIdFilter.getRequestId()
                .defaultIfEmpty("unknown")
                .map(requestId -> ApiResponse.success(data, requestId));
    }
}
