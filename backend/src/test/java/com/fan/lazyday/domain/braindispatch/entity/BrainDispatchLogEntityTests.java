package com.fan.lazyday.domain.braindispatch.entity;

import com.fan.lazyday.domain.braindispatch.po.BrainDispatchLogPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrainDispatchLogEntityTests {

    @Test
    @DisplayName("pending 记录可以标记为 sent")
    void markSent_fromPending_shouldSucceed() {
        BrainDispatchLogEntity entity = entityWithStatus(BrainDispatchStatus.PENDING);

        entity.markSent();

        assertThat(entity.getDelegate().getStatus()).isEqualTo(BrainDispatchStatus.SENT.value());
    }

    @Test
    @DisplayName("sent 记录收到成功 ACK 后可以标记为 acked 并写入 ackedTime")
    void markAcked_fromSent_shouldSucceed() {
        BrainDispatchLogEntity entity = entityWithStatus(BrainDispatchStatus.SENT);
        Instant now = Instant.parse("2026-05-01T00:00:00Z");

        entity.markAcked(now);

        assertThat(entity.getDelegate().getStatus()).isEqualTo(BrainDispatchStatus.ACKED.value());
        assertThat(entity.getDelegate().getAckedTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("sent 记录收到失败 ACK 后可以标记为 failed 并写入错误")
    void markFailed_fromSent_shouldSucceed() {
        BrainDispatchLogEntity entity = entityWithStatus(BrainDispatchStatus.SENT);

        entity.markFailed(1002, "unknown type");

        assertThat(entity.getDelegate().getStatus()).isEqualTo(BrainDispatchStatus.FAILED.value());
        assertThat(entity.getDelegate().getLastError()).isEqualTo("1002:unknown type");
    }

    @Test
    @DisplayName("sent 记录超时后可以标记为 timeout 并写入 ack timeout")
    void markTimeout_fromSent_shouldSucceed() {
        BrainDispatchLogEntity entity = entityWithStatus(BrainDispatchStatus.SENT);

        entity.markTimeout();

        assertThat(entity.getDelegate().getStatus()).isEqualTo(BrainDispatchStatus.TIMEOUT.value());
        assertThat(entity.getDelegate().getLastError()).isEqualTo("ack timeout");
    }

    @Test
    @DisplayName("终态记录拒绝再次扭转")
    void terminalStatus_shouldRejectFurtherTransitions() {
        assertThatThrownBy(() -> entityWithStatus(BrainDispatchStatus.ACKED).markSent())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> entityWithStatus(BrainDispatchStatus.FAILED).markAcked(Instant.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> entityWithStatus(BrainDispatchStatus.TIMEOUT).markFailed(1002, "late"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("跨态跳跃必须拒绝")
    void illegalJump_shouldReject() {
        assertThatThrownBy(() -> entityWithStatus(BrainDispatchStatus.PENDING).markAcked(Instant.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> entityWithStatus(BrainDispatchStatus.PENDING).markFailed(1002, "bad"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> entityWithStatus(BrainDispatchStatus.PENDING).markTimeout())
                .isInstanceOf(IllegalStateException.class);
    }

    private BrainDispatchLogEntity entityWithStatus(BrainDispatchStatus status) {
        return BrainDispatchLogEntity.fromPo(new BrainDispatchLogPO()
                .setId(1L)
                .setMsgId("1888")
                .setTenantId(7L)
                .setType(BrainDispatchType.CONFIG_UPDATE.value())
                .setPayload("{}")
                .setStatus(status.value()));
    }
}
