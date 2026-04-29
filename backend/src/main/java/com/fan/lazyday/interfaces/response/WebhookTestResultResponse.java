package com.fan.lazyday.interfaces.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookTestResultResponse {
    @JsonProperty("http_status")
    private Integer httpStatus;
    @JsonProperty("response_headers")
    private Map<String, String> responseHeaders;
    @JsonProperty("response_body_excerpt")
    private String responseBodyExcerpt;
    @JsonProperty("latency_ms")
    private long latencyMs;
    @JsonProperty("error_code")
    private String errorCode;
    private String error;
}
