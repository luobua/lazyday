package com.fan.lazyday.domain.tenant.po;

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
@Table("t_tenant")
public class Tenant extends BaseAllUserTime implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    private String name;
    private String status;
    private String planType;
    private String contactEmail;
}