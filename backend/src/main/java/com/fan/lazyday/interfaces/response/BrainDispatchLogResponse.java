package com.fan.lazyday.interfaces.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class BrainDispatchLogResponse {
    private String msgId;
    private Long tenantId;
    private String type;
    @JsonRawValue
    private String payload;
    private String status;
    private String lastError;
    private Instant createdTime;
    private Instant ackedTime;
}
