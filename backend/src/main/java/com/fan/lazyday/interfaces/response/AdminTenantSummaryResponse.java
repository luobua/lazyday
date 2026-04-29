package com.fan.lazyday.interfaces.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class AdminTenantSummaryResponse {
    private Long id;
    private String name;
    private String email;
    private String status;
    @JsonProperty("plan_id")
    private Long planId;
    @JsonProperty("plan_name")
    private String planName;
    @JsonProperty("created_time")
    private Instant createdTime;
}
