package com.fan.lazyday.edge.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
public class EdgeMessageDispatcher {

    private final ObjectMapper objectMapper;
    private final MessageIdLru messageIdLru;

    public EdgeMessageDispatcher(ObjectMapper objectMapper) {
        this(objectMapper, new MessageIdLru(1024));
    }

    EdgeMessageDispatcher(ObjectMapper objectMapper, MessageIdLru messageIdLru) {
        this.objectMapper = objectMapper;
        this.messageIdLru = messageIdLru;
    }

    public Mono<Void> dispatch(EnvelopeDTO envelope, Sinks.Many<EnvelopeDTO> outbound) {
        if ("HEARTBEAT".equals(envelope.getType())) {
            return Mono.empty();
        }
        if (!"CONFIG_UPDATE".equals(envelope.getType())) {
            outbound.tryEmitNext(ack(envelope, 1002, "unknown type"));
            return Mono.empty();
        }
        if (envelope.getTenantId() == null) {
            outbound.tryEmitNext(ack(envelope, 1001, "tenantId is required"));
            return Mono.empty();
        }
        if (!messageIdLru.addIfAbsent(envelope.getMsgId())) {
            outbound.tryEmitNext(ack(envelope, 0, "ok"));
            return Mono.empty();
        }
        log.info("Received CONFIG_UPDATE msgId={} tenantId={}", envelope.getMsgId(), envelope.getTenantId());
        outbound.tryEmitNext(ack(envelope, 0, "ok"));
        return Mono.empty();
    }

    private EnvelopeDTO ack(EnvelopeDTO source, int code, String message) {
        return new EnvelopeDTO()
                .setType("ACK")
                .setMsgId(source.getMsgId() + "-ack")
                .setTenantId(source.getTenantId())
                .setPayload(objectMapper.valueToTree(new AckPayloadDTO()
                        .setAckedMsgId(source.getMsgId())
                        .setCode(code)
                        .setMessage(message)));
    }
}
