package com.fan.lazyday.domain.braindispatch.entity;

import com.fan.lazyday.domain.braindispatch.po.BrainDispatchLogPO;
import com.fan.lazyday.infrastructure.domain.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
public class BrainDispatchLogEntity extends Entity<Long> {

    public static final Class<BrainDispatchLogPO> PO_CLASS = BrainDispatchLogPO.class;

    @Setter(AccessLevel.PROTECTED)
    private BrainDispatchLogPO delegate;

    public static BrainDispatchLogEntity fromPo(BrainDispatchLogPO po) {
        BrainDispatchLogEntity entity = new BrainDispatchLogEntity();
        entity.setDelegate(po);
        return entity;
    }

    @Override
    public Long getId() {
        return delegate == null ? null : delegate.getId();
    }

    public void markSent() {
        requireStatus(BrainDispatchStatus.PENDING, BrainDispatchStatus.SENT);
        delegate.setStatus(BrainDispatchStatus.SENT.value());
    }

    public void markAcked(Instant now) {
        requireStatus(BrainDispatchStatus.SENT, BrainDispatchStatus.ACKED);
        delegate.setStatus(BrainDispatchStatus.ACKED.value());
        delegate.setAckedTime(now);
    }

    public void markFailed(int code, String message) {
        requireStatus(BrainDispatchStatus.SENT, BrainDispatchStatus.FAILED);
        delegate.setStatus(BrainDispatchStatus.FAILED.value());
        delegate.setLastError(code + ":" + (message == null ? "" : message));
    }

    public void markTimeout() {
        requireStatus(BrainDispatchStatus.SENT, BrainDispatchStatus.TIMEOUT);
        delegate.setStatus(BrainDispatchStatus.TIMEOUT.value());
        delegate.setLastError("ack timeout");
    }

    private void requireStatus(BrainDispatchStatus expected, BrainDispatchStatus target) {
        BrainDispatchStatus current = BrainDispatchStatus.fromValue(delegate.getStatus());
        if (current != expected) {
            throw new IllegalStateException("Cannot transition brain dispatch log from "
                    + current.value() + " to " + target.value());
        }
    }
}
