package com.fan.lazyday.domain.braindispatch.repository;

import com.fan.lazyday.domain.braindispatch.entity.BrainDispatchLogEntity;
import com.fan.lazyday.domain.braindispatch.entity.BrainDispatchStatus;
import com.fan.lazyday.domain.braindispatch.po.BrainDispatchLogPO;
import com.fan.lazyday.support.PostgresSpringBootIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BrainDispatchLogRepositoryTest extends PostgresSpringBootIntegrationTestSupport {

    @Autowired
    private BrainDispatchLogRepository repository;

    @Test
    void updateStatus_withExpectedStatus_shouldNotOverwriteTimeout() {
        BrainDispatchLogPO stalePending = BrainDispatchLogRepository.newPending(
                System.currentTimeMillis(),
                "race-msg-1",
                7L,
                "CONFIG_UPDATE",
                "{\"hello\":\"edge\"}",
                Instant.now().minusSeconds(60)
        );

        StepVerifier.create(repository.insert(stalePending))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(repository.markTimeoutBefore(Instant.now()))
                .expectNext(1L)
                .verifyComplete();

        BrainDispatchLogEntity entity = BrainDispatchLogEntity.fromPo(stalePending);
        entity.markSent();

        StepVerifier.create(repository.updateStatus(entity.getDelegate(), BrainDispatchStatus.PENDING))
                .expectNext(0L)
                .verifyComplete();
        StepVerifier.create(repository.findByMsgId("race-msg-1"))
                .assertNext(log -> assertThat(log.getStatus()).isEqualTo(BrainDispatchStatus.TIMEOUT.value()))
                .verifyComplete();
    }
}
