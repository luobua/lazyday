package com.fan.lazyday.interfaces.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class AdminOverviewMetricsResponse {
    @JsonProperty("total_tenants")
    private Long totalTenants;
    @JsonProperty("active_tenants_7d")
    private Long activeTenants7d;
    @JsonProperty("today_calls")
    private Long todayCalls;
    @JsonProperty("today_success_rate")
    private Double todaySuccessRate;
    @JsonProperty("top_paths_today")
    private List<TopPath> topPathsToday = new ArrayList<>();

    @Getter
    @Setter
    public static class TopPath {
        private String path;
        @JsonProperty("call_count")
        private Long callCount;
    }
}
