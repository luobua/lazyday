package com.fan.lazyday.interfaces.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookConfigResponse {
    private Long id;
    private String name;
    private String url;
    @JsonProperty("event_types")
    private List<String> eventTypes;
    private String status;
    @JsonProperty("create_time")
    private Instant createTime;
    @JsonProperty("update_time")
    private Instant updateTime;
    private String secret;
    @JsonIgnore
    private String secretEncrypted;
}
