package com.fan.lazyday.application.service.braindispatch;

import com.fan.lazyday.domain.braindispatch.entity.BrainDispatchLogEntity;
import com.fan.lazyday.domain.braindispatch.entity.BrainDispatchStatus;
import com.fan.lazyday.domain.braindispatch.entity.BrainDispatchType;
import com.fan.lazyday.domain.braindispatch.po.BrainDispatchLogPO;
import com.fan.lazyday.domain.braindispatch.repository.BrainDispatchLogRepository;
import com.fan.lazyday.infrastructure.utils.id.SnowflakeIdWorker;
import com.fan.lazyday.interfaces.dto.braindispatch.AckPayloadDTO;
import com.fan.lazyday.interfaces.dto.braindispatch.EnvelopeDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrainDispatchService {

    private final BrainDispatchLogRepository repository;
    private final SnowflakeIdWorker snowflakeIdWorker;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public Mono<String> createPending(Long tenantId, JsonNode payload) {
        long id = snowflakeIdWorker.nextId();
        String msgId = Long.toString(id);
        BrainDispatchLogPO dispatchLog = BrainDispatchLogRepository.newPending(
                id,
                msgId,
                tenantId,
                BrainDispatchType.CONFIG_UPDATE.value(),
                payload == null ? "null" : payload.toString(),
                Instant.now()
        );
        return repository.insert(dispatchLog)
                .map(BrainDispatchLogPO::getMsgId)
                .doOnError(ex -> log.error("Failed to create brain dispatch log msgId={}", msgId, ex));
    }

    public EnvelopeDTO buildConfigUpdate(String msgId, Long tenantId, JsonNode payload) {
        return new EnvelopeDTO()
                .setType(BrainDispatchType.CONFIG_UPDATE.value())
                .setMsgId(msgId)
                .setTenantId(tenantId)
                .setPayload(payload);
    }

    public Mono<Void> onSendSuccess(String msgId) {
        return repository.findByMsgId(msgId)
                .filter(log -> BrainDispatchStatus.PENDING.value().equals(log.getStatus()))
                .flatMap(log -> {
                    BrainDispatchLogEntity entity = BrainDispatchLogEntity.fromPo(log);
                    entity.markSent();
                    return repository.updateStatus(entity.getDelegate(), BrainDispatchStatus.PENDING);
                })
                .then();
    }

    public Mono<Void> onAck(EnvelopeDTO envelope) {
        AckPayloadDTO payload = readAckPayload(envelope);
        if (payload == null || payload.getAckedMsgId() == null || payload.getAckedMsgId().isBlank()) {
            log.warn("Ignore ACK without ackedMsgId, msgId={}", envelope.getMsgId());
            return Mono.empty();
        }
        return repository.findByMsgId(payload.getAckedMsgId())
                .filter(log -> BrainDispatchStatus.SENT.value().equals(log.getStatus())
                        || BrainDispatchStatus.PENDING.value().equals(log.getStatus()))
                .flatMap(log -> {
                    BrainDispatchLogEntity entity = BrainDispatchLogEntity.fromPo(log);
                    if (BrainDispatchStatus.PENDING.value().equals(log.getStatus())) {
                        entity.markSent();
                    }
                    if (Integer.valueOf(0).equals(payload.getCode())) {
                        entity.markAcked(Instant.now());
                    } else {
                        entity.markFailed(payload.getCode() == null ? 1002 : payload.getCode(), payload.getMessage());
                    }
                    return repository.updateStatus(entity.getDelegate(), BrainDispatchStatus.PENDING, BrainDispatchStatus.SENT);
                })
                .then();
    }

    private AckPayloadDTO readAckPayload(EnvelopeDTO envelope) {
        if (envelope.getPayload() == null || envelope.getPayload().isNull()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(envelope.getPayload(), AckPayloadDTO.class);
        } catch (Exception ex) {
            log.warn("Invalid ACK payload msgId={}", envelope.getMsgId(), ex);
            return null;
        }
    }
}
