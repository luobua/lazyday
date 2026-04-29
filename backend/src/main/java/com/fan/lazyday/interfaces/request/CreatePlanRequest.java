package com.fan.lazyday.interfaces.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePlanRequest {
    @NotBlank
    private String name;
    @NotNull
    @Positive
    @JsonProperty("qps_limit")
    private Integer qpsLimit;
    @NotNull
    @Positive
    @JsonProperty("daily_limit")
    private Long dailyLimit;
    @NotNull
    @Positive
    @JsonProperty("monthly_limit")
    private Long monthlyLimit;
    @NotNull
    @Min(-1)
    @JsonProperty("max_app_keys")
    private Integer maxAppKeys;
}
