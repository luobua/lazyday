package com.fan.lazyday.domain.event;

import java.time.Instant;

public sealed interface DomainEvent permits AppKeyDisabledEvent, AppKeyRotatedEvent, TenantSuspendedEvent,
        TenantResumedEvent, QuotaExceededEvent, QuotaPlanChangedEvent, WebhookPermanentFailedEvent {

    Long tenantId();

    String eventType();

    Instant eventTime();
}
