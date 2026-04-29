package com.fan.lazyday.interfaces.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePlanRequest {
    private String name;
    @Positive
    @JsonProperty("qps_limit")
    private Integer qpsLimit;
    @Positive
    @JsonProperty("daily_limit")
    private Long dailyLimit;
    @Positive
    @JsonProperty("monthly_limit")
    private Long monthlyLimit;
    @Min(-1)
    @JsonProperty("max_app_keys")
    private Integer maxAppKeys;
}
