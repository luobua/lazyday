package com.fan.lazyday.domain.event;

import java.time.Instant;

public record WebhookPermanentFailedEvent(
        Long tenantId,
        Long eventId,
        Long configId,
        String failedEventType,
        Integer lastHttpStatus,
        String lastError,
        Instant eventTime
) implements DomainEvent {
    @Override
    public String eventType() {
        return "webhook.permanent_failed";
    }
}
