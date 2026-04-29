package com.fan.lazyday.domain.calllog.po;

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
@Table("t_call_log")
public class CallLog implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    private Long tenantId;
    private String appKey;
    private String path;
    private String method;
    private Short statusCode;
    private Integer latencyMs;
    private String clientIp;
    private String errorMsg;
    private Instant requestTime;
}
