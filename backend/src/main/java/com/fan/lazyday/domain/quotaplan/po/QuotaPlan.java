package com.fan.lazyday.domain.quotaplan.po;

import com.fan.lazyday.infrastructure.domain.po.BaseAllUserTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
@Accessors(chain = true)
@Table("t_quota_plan")
public class QuotaPlan extends BaseAllUserTime implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    private String name;
    private Integer qpsLimit;
    private Long dailyLimit;
    private Long monthlyLimit;
    private Integer maxAppKeys;
    private String status;
}
