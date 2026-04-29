package com.fan.lazyday.domain.event;

import java.time.Instant;

public record QuotaExceededEvent(
        Long tenantId,
        String period,
        Long limit,
        Long usage,
        Instant eventTime
) implements DomainEvent {
    @Override
    public String eventType() {
        return "quota.exceeded";
    }
}
