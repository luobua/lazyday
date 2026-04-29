package com.fan.lazyday.interfaces.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class AdminTenantDetailResponse {
    private Long id;
    private String name;
    private String email;
    private String status;
    @JsonProperty("created_time")
    private Instant createdTime;
    @JsonProperty("plan_id")
    private Long planId;
    @JsonProperty("plan_name")
    private String planName;
    @JsonProperty("qps_limit")
    private Integer qpsLimit;
    @JsonProperty("daily_limit")
    private Long dailyLimit;
    @JsonProperty("monthly_limit")
    private Long monthlyLimit;
    @JsonProperty("max_app_keys")
    private Integer maxAppKeys;
    @JsonProperty("daily_usage")
    private Long dailyUsage;
    @JsonProperty("monthly_usage")
    private Long monthlyUsage;
    @JsonProperty("app_key_count")
    private Long appKeyCount;
    @JsonProperty("tenant_admin_emails")
    private List<String> tenantAdminEmails;
}
