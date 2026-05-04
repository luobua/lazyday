package com.fan.lazyday.application.service.braindispatch;

import com.fan.lazyday.domain.braindispatch.po.BrainDispatchLogPO;
import com.fan.lazyday.domain.braindispatch.repository.BrainDispatchLogRepository;
import com.fan.lazyday.infrastructure.ws.EdgeConnectionRegistry;
import com.fan.lazyday.interfaces.response.BrainDispatchLogResponse;
import com.fan.lazyday.interfaces.response.EdgeStatusResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BrainDispatchQueryService {

    private final BrainDispatchLogRepository repository;
    private final EdgeConnectionRegistry edgeConnectionRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public Mono<Page<BrainDispatchLogResponse>> pageLogs(Long tenantId,
                                                         List<String> statuses,
                                                         Instant from,
                                                         Instant to,
                                                         String msgId,
                                                         int page,
                                                         int size) {
        return repository.pageLogs(tenantId, statuses, from, to, msgId, page, size)
                .map(logs -> logs.map(this::toResponse));
    }

    public Mono<BrainDispatchLogResponse> getLog(String msgId) {
        return repository.findByMsgId(msgId).map(this::toResponse);
    }

    public Mono<EdgeStatusResponse> getEdgeStatus() {
        EdgeStatusResponse response = new EdgeStatusResponse();
        response.setSessionCount(edgeConnectionRegistry.sessionCount());
        response.setConnected(response.getSessionCount() > 0);
        response.setLastSeenAgoMs(edgeConnectionRegistry.lastSeenAgoMs(Instant.now()));
        return Mono.just(response);
    }

    private BrainDispatchLogResponse toResponse(BrainDispatchLogPO po) {
        BrainDispatchLogResponse response = new BrainDispatchLogResponse();
        response.setMsgId(po.getMsgId());
        response.setTenantId(po.getTenantId());
        response.setType(po.getType());
        response.setPayload(readPayload(po.getPayload()));
        response.setStatus(po.getStatus());
        response.setLastError(po.getLastError());
        response.setCreatedTime(po.getCreateTime());
        response.setAckedTime(po.getAckedTime());
        return response;
    }

    private JsonNode readPayload(String payload) {
        try {
            if (payload == null || payload.isBlank()) return null;
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse brain dispatch payload", ex);
        }
    }
}
