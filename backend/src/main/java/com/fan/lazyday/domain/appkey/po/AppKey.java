package com.fan.lazyday.domain.appkey.po;

import com.fan.lazyday.infrastructure.domain.po.BaseAllUserTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@Accessors(chain = true)
@Table("t_app_key")
public class AppKey extends BaseAllUserTime implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    private Long tenantId;
    private String name;
    private String appKey;
    private String secretKeyEncrypted;
    private String secretKeyOld;
    private Instant rotatedAt;
    private Instant gracePeriodEnd;
    private String status;
    private String scopes;
}