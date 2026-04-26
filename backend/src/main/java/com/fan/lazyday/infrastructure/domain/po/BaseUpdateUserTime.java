package com.fan.lazyday.infrastructure.domain.po;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
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
public class BaseUpdateUserTime{
    @LastModifiedBy
    @Column("update_user")
    private UUID updateUser;
    @LastModifiedDate
    @Column("update_time")
    private Instant updateTime;
}
