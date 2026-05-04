package com.fan.lazyday.interfaces.handler.internal;

import com.fan.lazyday.application.service.braindispatch.BrainDispatchService;
import com.fan.lazyday.application.service.braindispatch.BrainDispatchQueryService;
import com.fan.lazyday.infrastructure.config.path.RequestMappingInternalV1;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.infrastructure.ws.EdgeConnectionRegistry;
import com.fan.lazyday.interfaces.api.internal.InternalDispatchApi;
import com.fan.lazyday.interfaces.dto.braindispatch.EnvelopeDTO;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.BrainDispatchLogResponse;
import com.fan.lazyday.interfaces.response.EdgeStatusResponse;
import com.fan.lazyday.interfaces.response.PageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMappingInternalV1
@RequiredArgsConstructor
public class InternalDispatchHandler implements InternalDispatchApi {

    private final BrainDispatchService brainDispatchService;
    private final BrainDispatchQueryService brainDispatchQueryService;
    private final EdgeConnectionRegistry edgeConnectionRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public Mono<ApiResponse<Map<String, String>>> dispatch(Long tenantId, Mono<String> payload) {
        return payload
                .flatMap(this::parseJson)
                .flatMap(body -> brainDispatchService.createPending(tenantId, body)
                        .flatMap(msgId -> broadcast(tenantId, body, msgId)
                                .thenReturn(Map.of("msgId", msgId))))
                .flatMap(this::wrapSuccess);
    }

    private Mono<JsonNode> parseJson(String rawPayload) {
        try {
            return Mono.just(objectMapper.readTree(rawPayload));
        } catch (Exception ex) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload", ex));
        }
    }

    private Mono<Void> broadcast(Long tenantId, JsonNode body, String msgId) {
        EnvelopeDTO envelope = brainDispatchService.buildConfigUpdate(msgId, tenantId, body);
        if (edgeConnectionRegistry.sessionCount() <= 0) {
            return Mono.empty();
        }
        return edgeConnectionRegistry.broadcast(envelope)
                .flatMap(count -> count > 0 ? brainDispatchService.onSendSuccess(msgId) : Mono.empty())
                .then();
    }

    @Override
    public Mono<ApiResponse<PageResponse<BrainDispatchLogResponse>>> listLogs(Long tenantId,
                                                                              List<String> statuses,
                                                                              Instant from,
                                                                              Instant to,
                                                                              String msgId,
                                                                              int page,
                                                                              int size) {
        return brainDispatchQueryService.pageLogs(tenantId, statuses, from, to, msgId, page, size)
                .map(result -> PageResponse.of(
                        result.getContent(),
                        result.getTotalElements(),
                        result.getNumber(),
                        result.getSize(),
                        result.getTotalPages()
                ))
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<BrainDispatchLogResponse>> getLog(String msgId) {
        return brainDispatchQueryService.getLog(msgId).flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<EdgeStatusResponse>> getEdgeStatus() {
        return brainDispatchQueryService.getEdgeStatus().flatMap(this::wrapSuccess);
    }

    private <T> Mono<ApiResponse<T>> wrapSuccess(T data) {
        return RequestIdFilter.getRequestId()
                .defaultIfEmpty("unknown")
                .map(requestId -> ApiResponse.success(data, requestId));
    }
}
