package com.fan.lazyday.infrastructure.ws;

import com.fan.lazyday.interfaces.dto.braindispatch.EnvelopeDTO;
import lombok.Getter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class EdgeConnectionRegistry {

    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public Connection register(String sessionId) {
        Connection connection = new Connection(sessionId, Sinks.many().replay().limit(1024), Instant.now());
        connections.put(sessionId, connection);
        return connection;
    }

    public void unregister(String sessionId) {
        Connection connection = connections.remove(sessionId);
        if (connection != null) {
            connection.getSink().tryEmitComplete();
        }
    }

    public void markSeen(String sessionId) {
        Connection connection = connections.get(sessionId);
        if (connection != null) {
            connection.lastSeen = Instant.now();
        }
    }

    public void markReady(String sessionId) {
        Connection connection = connections.get(sessionId);
        if (connection != null) {
            connection.ready = true;
        }
    }

    public Mono<Integer> broadcast(EnvelopeDTO envelope) {
        List<Connection> active = connections.values().stream()
                .filter(Connection::isReady)
                .toList();
        AtomicInteger accepted = new AtomicInteger();
        active.forEach(connection -> {
            Sinks.EmitResult result = connection.getSink().tryEmitNext(envelope);
            if (result.isSuccess()) {
                accepted.incrementAndGet();
            }
        });
        return Mono.just(accepted.get());
    }

    public int sessionCount() {
        return (int) connections.values().stream()
                .filter(Connection::isReady)
                .count();
    }

    public Instant lastSeen() {
        return connections.values().stream()
                .map(Connection::getLastSeen)
                .max(Instant::compareTo)
                .orElse(null);
    }

    public Long lastSeenAgoMs(Instant now) {
        Instant lastSeen = lastSeen();
        return lastSeen == null ? null : Duration.between(lastSeen, now).toMillis();
    }

    @Getter
    public static class Connection {
        private final String sessionId;
        private final Sinks.Many<EnvelopeDTO> sink;
        private volatile Instant lastSeen;
        private volatile boolean ready;

        Connection(String sessionId, Sinks.Many<EnvelopeDTO> sink, Instant lastSeen) {
            this.sessionId = sessionId;
            this.sink = sink;
            this.lastSeen = lastSeen;
        }
    }
}
