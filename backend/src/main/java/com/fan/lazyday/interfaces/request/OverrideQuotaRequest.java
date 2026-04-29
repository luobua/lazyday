package com.fan.lazyday.interfaces.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OverrideQuotaRequest {
    @NotNull
    @JsonProperty("plan_id")
    private Long planId;

    @JsonProperty("custom_qps_limit")
    private Integer customQpsLimit;
    @JsonProperty("custom_daily_limit")
    private Long customDailyLimit;
    @JsonProperty("custom_monthly_limit")
    private Long customMonthlyLimit;
    @JsonProperty("custom_max_app_keys")
    private Integer customMaxAppKeys;
}
