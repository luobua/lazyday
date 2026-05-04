package com.fan.lazyday.application.service.braindispatch;

import com.fan.lazyday.domain.braindispatch.po.BrainDispatchLogPO;
import com.fan.lazyday.domain.braindispatch.repository.BrainDispatchLogRepository;
import com.fan.lazyday.infrastructure.ws.EdgeConnectionRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrainDispatchQueryServiceTest {

    @Test
    void getLog_shouldExposeFullPayload() {
        BrainDispatchLogRepository repository = mock(BrainDispatchLogRepository.class);
        EdgeConnectionRegistry registry = new EdgeConnectionRegistry();
        BrainDispatchQueryService service = new BrainDispatchQueryService(repository, registry);
        String payload = "{\"hello\":\"edge\",\"nested\":{\"value\":42}}";
        BrainDispatchLogPO log = new BrainDispatchLogPO()
                .setId(1L)
                .setMsgId("1888")
                .setTenantId(7L)
                .setType("CONFIG_UPDATE")
                .setPayload(payload)
                .setStatus("acked");
        log.setCreateTime(Instant.parse("2026-05-01T00:00:00Z"));

        when(repository.findByMsgId("1888")).thenReturn(Mono.just(log));

        StepVerifier.create(service.getLog("1888"))
                .assertNext(response -> {
                    assertThat(response.getPayload().get("hello").asText()).isEqualTo("edge");
                    assertThat(response.getPayload().get("nested").get("value").asInt()).isEqualTo(42);
                })
                .verifyComplete();
    }

    @Test
    void getEdgeStatus_withoutConnection_shouldReturnDisconnected() {
        BrainDispatchLogRepository repository = mock(BrainDispatchLogRepository.class);
        BrainDispatchQueryService service = new BrainDispatchQueryService(repository, new EdgeConnectionRegistry());

        StepVerifier.create(service.getEdgeStatus())
                .assertNext(status -> {
                    assertThat(status.isConnected()).isFalse();
                    assertThat(status.getSessionCount()).isZero();
                    assertThat(status.getLastSeenAgoMs()).isNull();
                })
                .verifyComplete();
    }
}
