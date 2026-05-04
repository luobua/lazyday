package com.fan.lazyday.infrastructure.ws;

import com.fan.lazyday.application.service.braindispatch.BrainDispatchService;
import com.fan.lazyday.domain.braindispatch.entity.BrainDispatchType;
import com.fan.lazyday.interfaces.dto.braindispatch.EnvelopeDTO;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class EdgeWebSocketHandler implements WebSocketHandler {

    private final EdgeConnectionRegistry registry;
    private final BrainDispatchService brainDispatchService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        EdgeConnectionRegistry.Connection connection = registry.register(session.getId());
        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(text -> handleInbound(session.getId(), text))
                .then();
        Mono<Void> outbound = session.send(connection.getSink().asFlux()
                .doOnSubscribe(subscription -> registry.markReady(session.getId()))
                .map(envelope -> session.textMessage(write(envelope))));
        return Mono.when(inbound, outbound)
                .doFinally(signal -> registry.unregister(session.getId()));
    }

    private Mono<Void> handleInbound(String sessionId, String text) {
        EnvelopeDTO envelope;
        try {
            envelope = objectMapper.readValue(text, EnvelopeDTO.class);
        } catch (Exception ex) {
            log.warn("Invalid edge envelope: {}", text, ex);
            return Mono.empty();
        }
        if (envelope.getType() == null || envelope.getMsgId() == null) {
            log.warn("Invalid edge envelope missing type or msgId: {}", text);
            return Mono.empty();
        }
        registry.markSeen(sessionId);
        if (BrainDispatchType.HEARTBEAT.value().equals(envelope.getType())) {
            return Mono.empty();
        }
        if (BrainDispatchType.ACK.value().equals(envelope.getType())) {
            return brainDispatchService.onAck(envelope);
        }
        log.warn("Unsupported inbound edge envelope type: {}", envelope.getType());
        return Mono.empty();
    }

    private String write(EnvelopeDTO envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write edge envelope", ex);
        }
    }
}
