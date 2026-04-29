package com.fan.lazyday.interfaces.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateWebhookRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String url;
    @NotEmpty
    @JsonProperty("event_types")
    private List<String> eventTypes;
}
