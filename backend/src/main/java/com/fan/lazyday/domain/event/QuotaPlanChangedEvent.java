package com.fan.lazyday.domain.event;

import java.time.Instant;

public record QuotaPlanChangedEvent(
        Long tenantId,
        Long previousPlanId,
        Long newPlanId,
        Instant changeTime
) implements DomainEvent {
    @Override
    public String eventType() {
        return "quota.plan_changed";
    }

    @Override
    public Instant eventTime() {
        return changeTime;
    }
}
