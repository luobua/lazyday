package com.fan.lazyday.domain.event;

import java.time.Instant;

public record AppKeyRotatedEvent(
        Long tenantId,
        Long appKeyId,
        Instant rotatedTime,
        Instant previousSecretGraceUntil
) implements DomainEvent {
    @Override
    public String eventType() {
        return "appkey.rotated";
    }

    @Override
    public Instant eventTime() {
        return rotatedTime;
    }
}
