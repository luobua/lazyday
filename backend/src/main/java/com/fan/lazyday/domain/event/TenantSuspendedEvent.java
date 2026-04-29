package com.fan.lazyday.domain.event;

import java.time.Instant;

public record TenantSuspendedEvent(Long tenantId, Instant eventTime) implements DomainEvent {
    @Override
    public String eventType() {
        return "tenant.suspended";
    }
}
