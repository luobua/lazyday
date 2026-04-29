package com.fan.lazyday.domain.event;

import java.time.Instant;

public record AppKeyDisabledEvent(
        Long tenantId,
        Long appKeyId,
        String appKeyValue,
        Instant disabledTime
) implements DomainEvent {
    @Override
    public String eventType() {
        return "appkey.disabled";
    }

    @Override
    public Instant eventTime() {
        return disabledTime;
    }
}
