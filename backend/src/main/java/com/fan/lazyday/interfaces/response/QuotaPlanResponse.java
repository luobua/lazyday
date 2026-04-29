package com.fan.lazyday.interfaces.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class QuotaPlanResponse {
    private Long id;
    private String name;
    @JsonProperty("qps_limit")
    private Integer qpsLimit;
    @JsonProperty("daily_limit")
    private Long dailyLimit;
    @JsonProperty("monthly_limit")
    private Long monthlyLimit;
    @JsonProperty("max_app_keys")
    private Integer maxAppKeys;
    private String status;
    @JsonProperty("create_time")
    private Instant createTime;
    @JsonProperty("binding_count")
    private Long bindingCount;
}
