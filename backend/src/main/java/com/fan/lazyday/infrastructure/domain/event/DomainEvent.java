package com.fan.lazyday.infrastructure.domain.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;


/**
 * @author bufanqi
 */
@Getter
@Setter
@RequiredArgsConstructor
@SuppressWarnings("all")
public abstract class DomainEvent <T> {
    private final UUID aggregateId;
    private final T source;
    private final String type;
    private final UUID operationUser;
    Instant occurredOn = Instant.now();
}