package com.fan.lazyday.infrastructure.domain;

import lombok.Getter;

@Getter
public abstract class Entity<ID> {
    protected ID id;
}