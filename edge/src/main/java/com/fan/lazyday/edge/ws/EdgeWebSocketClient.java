package com.fan.lazyday.edge.ws;

import com.fan.lazyday.edge.config.EdgeProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class EdgeWebSocketClient {

    private final EdgeProperties properties;
    private final EdgeMessageDispatcher dispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();

    @Getter
    private final Sinks.Many<EnvelopeDTO> outbound = Sinks.many().multicast().onBackpressureBuffer();

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.isAutoStart()) {
            return;
        }
        connectForever().subscribe();
        startHeartbeat();
    }

    public Mono<Void> connectForever() {
        URI uri = URI.create(properties.getBackendWsUrl() + "/ws/edge");
        return Mono.defer(() -> client.execute(uri, session -> {
                    log.info("Connected to backend websocket {}", uri);
                    Mono<Void> send = session.send(outbound.asFlux()
                            .map(envelope -> session.textMessage(write(envelope))));
                    Mono<Void> receive = session.receive()
                            .map(message -> read(message.getPayloadAsText()))
                            .flatMap(envelope -> dispatcher.dispatch(envelope, outbound))
                            .then();
                    return Mono.when(send, receive);
                }).then(Mono.<Void>error(new IllegalStateException("Backend websocket completed"))))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, properties.getInitialBackoff())
                        .maxBackoff(properties.getMaxBackoff())
                        .doBeforeRetry(signal -> log.warn("Backend websocket disconnected, retrying", signal.failure())));
    }

    public Flux<EnvelopeDTO> inbound(Flux<String> frames) {
        return frames.map(this::read);
    }

    private void startHeartbeat() {
        Flux.interval(Duration.ZERO, properties.getHeartbeatInterval())
                .map(tick -> new EnvelopeDTO()
                        .setType("HEARTBEAT")
                        .setMsgId("heartbeat-" + System.currentTimeMillis())
                        .setTenantId(null)
                        .setPayload(null))
                .subscribe(outbound::tryEmitNext);
    }

    private EnvelopeDTO read(String text) {
        try {
            return objectMapper.readValue(text, EnvelopeDTO.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid backend envelope", ex);
        }
    }

    private String write(EnvelopeDTO envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write envelope", ex);
        }
    }
}
