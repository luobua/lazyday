package com.fan.lazyday.infrastructure.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * @author chenbin
 */
@Getter
@Setter
public abstract class BaseEntity<ID extends Serializable> extends Entity<ID> {

    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PUBLIC)
    protected boolean isNew = false;

    protected String createUser;

    protected String updateUser;

    protected Instant createTime;

    protected Instant updateTime;

    protected int deleted;

    public void setId(ID id) {
        this.id = id;
    }
}
