package com.fan.lazyday.interfaces.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateWebhookRequest {
    private String name;
    private String url;
    @JsonProperty("event_types")
    private List<String> eventTypes;
    private String status;
}
