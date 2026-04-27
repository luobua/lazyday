package com.fan.lazyday.interfaces.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppKeyResponse {
    private Long id;
    private String name;
    @JsonProperty("app_key")
    private String appKey;
    @JsonProperty("secret_key")
    private String secretKey;
    private String status;
    private String scopes;
    @JsonProperty("create_time")
    private Instant createTime;
}
