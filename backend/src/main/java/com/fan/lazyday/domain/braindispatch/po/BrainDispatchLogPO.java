package com.fan.lazyday.domain.braindispatch.po;

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
@Table("t_brain_dispatch_log")
public class BrainDispatchLogPO extends BaseAllUserTime implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    private String msgId;
    private Long tenantId;
    private String type;
    private String payload;
    private String status;
    private String lastError;
    private Instant ackedTime;
}
