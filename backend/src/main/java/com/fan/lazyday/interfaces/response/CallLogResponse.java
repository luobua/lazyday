package com.fan.lazyday.interfaces.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class CallLogResponse {
    private Long id;
    @JsonProperty("app_key")
    private String appKey;
    private String path;
    private String method;
    @JsonProperty("status_code")
    private Short statusCode;
    @JsonProperty("latency_ms")
    private Integer latencyMs;
    @JsonProperty("client_ip")
    private String clientIp;
    @JsonProperty("request_time")
    private Instant requestTime;
}
