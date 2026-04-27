package com.fan.lazyday.interfaces.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserInfoResponse {
    private Long id;
    private String username;
    private String email;
    private String role;
    @JsonProperty("tenant_id")
    private Long tenantId;
}
