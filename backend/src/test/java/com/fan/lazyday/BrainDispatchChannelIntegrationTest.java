package com.fan.lazyday;

import com.fan.lazyday.domain.braindispatch.po.BrainDispatchLogPO;
import com.fan.lazyday.domain.braindispatch.repository.BrainDispatchLogRepository;
import com.fan.lazyday.infrastructure.ws.EdgeConnectionRegistry;
import com.fan.lazyday.interfaces.dto.braindispatch.AckPayloadDTO;
import com.fan.lazyday.interfaces.dto.braindispatch.EnvelopeDTO;
import com.fan.lazyday.support.PostgresSpringBootIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;

class BrainDispatchChannelIntegrationTest extends PostgresSpringBootIntegrationTestSupport {

    private static final String INTERNAL_API_KEY = "lazyday-internal-dev-key-32-chars";

    @Autowired
    private BrainDispatchLogRepository dispatchLogRepository;
    @Autowired
    private EdgeConnectionRegistry edgeConnectionRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void dispatchPost_whenEdgeConnected_shouldAckWithinOneSecond() throws Exception {
        TestEdge edge = connectEdge(AckMode.SINGLE);
        try {
            waitForEdgeConnection();

            String response = webTestClient().post()
                    .uri("/internal/v1/dispatch/1")
                    .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"hello\":\"edge\"}")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            String msgId = objectMapper.readTree(response).path("data").path("msgId").asText();
            assertThat(msgId).isNotBlank();

            BrainDispatchLogPO log = waitForStatus(msgId, "acked", Duration.ofSeconds(1));
            assertThat(log.getTenantId()).isEqualTo(1L);
            assertThat(objectMapper.readTree(log.getPayload()).path("hello").asText()).isEqualTo("edge");
            assertThat(log.getAckedTime()).isNotNull();
        } finally {
            edge.dispose();
        }
    }

    @Test
    void dispatchPost_whenEdgeDoesNotAck_shouldTimeoutAfterThirtySeconds() throws Exception {
        TestEdge edge = connectEdge(AckMode.NONE);
        try {
            waitForEdgeConnection();

            String response = webTestClient().post()
                    .uri("/internal/v1/dispatch/1")
                    .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"hello\":\"edge\"}")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            String msgId = objectMapper.readTree(response).path("data").path("msgId").asText();
            waitForStatus(msgId, "sent", Duration.ofSeconds(1));
            BrainDispatchLogPO log = waitForStatus(msgId, "timeout", Duration.ofSeconds(35));
            assertThat(log.getLastError()).isEqualTo("ack timeout");
        } finally {
            edge.dispose();
        }
    }

    @Test
    void duplicateAck_shouldKeepSingleAckedRecord() throws Exception {
        TestEdge edge = connectEdge(AckMode.DUPLICATE);
        try {
            waitForEdgeConnection();

            String response = webTestClient().post()
                    .uri("/internal/v1/dispatch/1")
                    .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"hello\":\"edge\"}")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            String msgId = objectMapper.readTree(response).path("data").path("msgId").asText();
            waitForStatus(msgId, "acked", Duration.ofSeconds(1));

            Long count = databaseClient.sql("SELECT COUNT(*) AS c FROM t_brain_dispatch_log WHERE msg_id = :msgId")
                    .bind("msgId", msgId)
                    .map(row -> row.get("c", Long.class))
                    .one()
                    .block(Duration.ofSeconds(1));
            assertThat(count).isEqualTo(1L);
            assertThat(edge.acksSent()).isEqualTo(2);
        } finally {
            edge.dispose();
        }
    }

    @Test
    void edgeReconnect_shouldRestoreSessionWithinThirtySeconds() throws Exception {
        TestEdge first = connectEdge(AckMode.SINGLE);
        waitForEdgeConnection();
        first.dispose();
        waitForNoEdgeConnection();

        TestEdge second = connectEdge(AckMode.SINGLE);
        try {
            waitForEdgeConnection();

            String response = webTestClient().post()
                    .uri("/internal/v1/dispatch/1")
                    .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"hello\":\"edge\"}")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            String msgId = objectMapper.readTree(response).path("data").path("msgId").asText();
            waitForStatus(msgId, "acked", Duration.ofSeconds(1));
        } finally {
            second.dispose();
        }
    }

    private TestEdge connectEdge(AckMode ackMode) {
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        URI uri = URI.create("ws://127.0.0.1:" + port + "/ws/edge");
        Sinks.Many<EnvelopeDTO> outbound = Sinks.many().multicast().onBackpressureBuffer();
        Sinks.One<Void> sendReady = Sinks.one();
        Sinks.One<Void> receiveReady = Sinks.one();
        AtomicInteger ackCounter = new AtomicInteger();
        Disposable disposable = client.execute(uri, session -> {
                    Mono<Void> send = session.send(outbound.asFlux()
                            .doOnSubscribe(subscription -> sendReady.tryEmitEmpty())
                            .map(envelope -> session.textMessage(write(envelope))));
                    Mono<Void> receive = session.receive()
                            .doOnSubscribe(subscription -> receiveReady.tryEmitEmpty())
                            .map(message -> message.getPayloadAsText())
                            .flatMap(text -> emitAck(text, outbound, ackMode, ackCounter))
                            .then();
                    return Mono.when(send, receive);
                })
                .subscribe();
        Mono.when(sendReady.asMono(), receiveReady.asMono()).block(Duration.ofSeconds(5));
        return new TestEdge(disposable, ackCounter);
    }

    private Mono<Void> emitAck(String text, Sinks.Many<EnvelopeDTO> outbound, AckMode ackMode, AtomicInteger ackCounter) {
        try {
            EnvelopeDTO envelope = objectMapper.readValue(text, EnvelopeDTO.class);
            if (!"CONFIG_UPDATE".equals(envelope.getType())) {
                return Mono.empty();
            }
            if (ackMode == AckMode.SINGLE || ackMode == AckMode.DUPLICATE) {
                outbound.tryEmitNext(ack(envelope));
                ackCounter.incrementAndGet();
            }
            if (ackMode == AckMode.DUPLICATE) {
                outbound.tryEmitNext(ack(envelope));
                ackCounter.incrementAndGet();
            }
            return Mono.empty();
        } catch (Exception ex) {
            return Mono.error(ex);
        }
    }

    private EnvelopeDTO ack(EnvelopeDTO envelope) {
        return new EnvelopeDTO()
                .setType("ACK")
                .setMsgId(envelope.getMsgId() + "-ack")
                .setTenantId(envelope.getTenantId())
                .setPayload(objectMapper.valueToTree(new AckPayloadDTO()
                        .setAckedMsgId(envelope.getMsgId())
                        .setCode(0)
                        .setMessage("ok")));
    }

    private String write(EnvelopeDTO envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write test edge envelope", ex);
        }
    }

    private void waitForEdgeConnection() {
        waitUntil(() -> edgeConnectionRegistry.sessionCount() > 0, Duration.ofSeconds(5));
    }

    private void waitForNoEdgeConnection() {
        waitUntil(() -> edgeConnectionRegistry.sessionCount() == 0, Duration.ofSeconds(5));
    }

    private void waitUntil(BooleanSupplier assertion, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (assertion.getAsBoolean()) {
                return;
            }
            LockSupport.parkNanos(Duration.ofMillis(50).toNanos());
        }
        throw new AssertionError("Condition was not met within " + timeout);
    }

    private BrainDispatchLogPO waitForStatus(String msgId, String expectedStatus, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        BrainDispatchLogPO last = null;
        while (System.nanoTime() < deadline) {
            last = dispatchLogRepository.findByMsgId(msgId).block(Duration.ofSeconds(1));
            if (last != null && expectedStatus.equals(last.getStatus())) {
                return last;
            }
            LockSupport.parkNanos(Duration.ofMillis(50).toNanos());
        }
        String actualStatus = last == null ? "<missing>" : last.getStatus();
        throw new AssertionError("Expected dispatch log " + msgId + " status " + expectedStatus + " but was " + actualStatus);
    }

    private enum AckMode {
        NONE,
        SINGLE,
        DUPLICATE
    }

    private final class TestEdge {
        private final Disposable disposable;
        private final AtomicInteger ackCounter;

        private TestEdge(Disposable disposable, AtomicInteger ackCounter) {
            this.disposable = disposable;
            this.ackCounter = ackCounter;
        }

        private void dispose() {
            disposable.dispose();
        }

        private int acksSent() {
            return ackCounter.get();
        }
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
