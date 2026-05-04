package com.fan.lazyday.edge.ws;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class EnvelopeDTO {
    private String type;
    private String msgId;
    private Long tenantId;
    private JsonNode payload;
}
