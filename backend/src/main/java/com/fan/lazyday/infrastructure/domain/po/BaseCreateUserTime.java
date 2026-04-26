package com.fan.lazyday.infrastructure.domain.po;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.relational.core.mapping.Column;

import java.time.Instant;
import java.util.UUID;

/**
 * @author fan
 * @date 2022年10月15日 14:40
 */
@Getter
@Setter
@Accessors(chain = true)
public class BaseCreateUserTime{
    @Column("create_user")
    @CreatedBy
    public UUID createUser;
    @Column("create_time")
    @CreatedDate
    private Instant createTime;
}
