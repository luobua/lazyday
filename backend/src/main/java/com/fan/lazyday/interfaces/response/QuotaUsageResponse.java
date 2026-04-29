package com.fan.lazyday.interfaces.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuotaUsageResponse {
    @JsonProperty("plan_name")
    private String planName;
    @JsonProperty("qps_limit")
    private int qpsLimit;
    @JsonProperty("daily_limit")
    private long dailyLimit;
    @JsonProperty("monthly_limit")
    private long monthlyLimit;
    @JsonProperty("max_app_keys")
    private int maxAppKeys;
    @JsonProperty("daily_used")
    private long dailyUsed;
    @JsonProperty("monthly_used")
    private long monthlyUsed;
}
