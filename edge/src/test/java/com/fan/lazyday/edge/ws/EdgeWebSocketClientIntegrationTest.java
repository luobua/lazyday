package com.fan.lazyday.edge.ws;

import com.fan.lazyday.edge.config.EdgeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = EdgeWebSocketClientIntegrationTest.TestConfig.class)
class EdgeWebSocketClientIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger connections = new AtomicInteger();
    private final AtomicReference<EnvelopeDTO> lastAck = new AtomicReference<>();
    private final AtomicBoolean closeFirstConnection = new AtomicBoolean();

    private EdgeWebSocketClient edgeClient;

    private DisposableServer server;
    private Disposable clientSubscription;

    @BeforeEach
    void setUp() {
        connections.set(0);
        lastAck.set(null);
        closeFirstConnection.set(false);
        edgeClient = new EdgeWebSocketClient(testProperties(), new EdgeMessageDispatcher(objectMapper));
        server = startServer();
    }

    @AfterEach
    void tearDown() {
        if (clientSubscription != null) {
            clientSubscription.dispose();
        }
        if (server != null) {
            server.disposeNow();
        }
    }

    @Test
    void connectForever_shouldConnectAndAckConfigUpdateWithinFiveSeconds() {
        clientSubscription = edgeClient.connectForever().subscribe();

        waitUntil(() -> connections.get() > 0, Duration.ofSeconds(5));
        waitUntil(() -> lastAck.get() != null, Duration.ofSeconds(5));

        EnvelopeDTO ack = lastAck.get();
        assertThat(ack.getType()).isEqualTo("ACK");
        assertThat(ack.getPayload().get("ackedMsgId").asText()).isEqualTo("msg-1");
        assertThat(ack.getPayload().get("code").asInt()).isZero();
    }

    @Test
    void connectForever_shouldReconnectAfterConnectionCompletes() {
        closeFirstConnection.set(true);
        clientSubscription = edgeClient.connectForever().subscribe();

        waitUntil(() -> connections.get() >= 2, Duration.ofSeconds(30));
    }

    private DisposableServer startServer() {
        return HttpServer.create()
                .host("127.0.0.1")
                .port(19081)
                .route(routes -> routes.ws("/ws/edge", (inbound, outbound) -> {
                    int connectionNumber = connections.incrementAndGet();
                    if (closeFirstConnection.get() && connectionNumber == 1) {
                        return Mono.delay(Duration.ofMillis(100)).then();
                    }
                    Mono<Void> receive = inbound.receive()
                            .asString()
                            .map(this::read)
                            .doOnNext(lastAck::set)
                            .then();
                    Mono<Void> send = outbound.sendString(Mono.just(write(configUpdate("msg-1")))).then();
                    return Mono.when(receive, send);
                }))
                .bindNow(Duration.ofSeconds(5));
    }

    private EnvelopeDTO configUpdate(String msgId) {
        return new EnvelopeDTO()
                .setType("CONFIG_UPDATE")
                .setMsgId(msgId)
                .setTenantId(7L)
                .setPayload(objectMapper.createObjectNode().put("hello", "edge"));
    }

    private EnvelopeDTO read(String text) {
        try {
            return objectMapper.readValue(text, EnvelopeDTO.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to read envelope", ex);
        }
    }

    private String write(EnvelopeDTO envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write envelope", ex);
        }
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

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }

    @SpringBootConfiguration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        EdgeProperties edgeProperties() {
            EdgeProperties properties = new EdgeProperties();
            properties.setAutoStart(false);
            properties.setBackendWsUrl("ws://127.0.0.1:19081");
            properties.setInitialBackoff(Duration.ofMillis(100));
            properties.setMaxBackoff(Duration.ofMillis(500));
            properties.setHeartbeatInterval(Duration.ofSeconds(1));
            return properties;
        }

        @Bean
        EdgeMessageDispatcher edgeMessageDispatcher(ObjectMapper objectMapper) {
            return new EdgeMessageDispatcher(objectMapper);
        }

        @Bean
        EdgeWebSocketClient edgeWebSocketClient(EdgeProperties properties, EdgeMessageDispatcher dispatcher) {
            return new EdgeWebSocketClient(properties, dispatcher);
        }
    }

    private static EdgeProperties testProperties() {
        EdgeProperties properties = new EdgeProperties();
        properties.setAutoStart(false);
        properties.setBackendWsUrl("ws://127.0.0.1:19081");
        properties.setInitialBackoff(Duration.ofMillis(100));
        properties.setMaxBackoff(Duration.ofMillis(500));
        properties.setHeartbeatInterval(Duration.ofSeconds(1));
        return properties;
    }
}
