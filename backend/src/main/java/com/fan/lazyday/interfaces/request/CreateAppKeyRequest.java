package com.fan.lazyday.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAppKeyRequest {
    @NotBlank
    private String name;
    private String scopes;
}
