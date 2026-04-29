package com.fan.lazyday.interfaces.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CallLogStatsResponse {
    private String granularity;
    private long total;
    @JsonProperty("success_count")
    private long successCount;
    @JsonProperty("client_error_count")
    private long clientErrorCount;
    @JsonProperty("server_error_count")
    private long serverErrorCount;
    @JsonProperty("avg_latency_ms")
    private double avgLatencyMs;
    @JsonProperty("daily_volume")
    private List<DailyVolumeItem> dailyVolume;
    @JsonProperty("top_paths")
    private List<TopPathItem> topPaths;
    private List<BucketItem> buckets;

    @Getter
    @Setter
    public static class DailyVolumeItem {
        private LocalDate day;
        private long count;
        @JsonProperty("success_count")
        private long successCount;
    }

    @Getter
    @Setter
    public static class TopPathItem {
        private String path;
        private long count;
    }

    @Getter
    @Setter
    public static class BucketItem {
        @JsonProperty("bucket_time")
        private Instant bucketTime;
        @JsonProperty("total_count")
        private long totalCount;
        @JsonProperty("success_count")
        private long successCount;
        @JsonProperty("error_count")
        private long errorCount;
    }
}
