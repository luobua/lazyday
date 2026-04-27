package com.fan.lazyday.interfaces.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTenantRequest {
    private String name;
    private String contactEmail;
}
