package com.fan.lazyday.interfaces.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantResponse {
    private Long id;
    private String name;
    private String status;
    @JsonProperty("plan_type")
    private String planType;
    @JsonProperty("contact_email")
    private String contactEmail;
}
