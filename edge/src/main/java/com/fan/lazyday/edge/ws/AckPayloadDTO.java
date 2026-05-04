package com.fan.lazyday.edge.ws;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class AckPayloadDTO {
    private String ackedMsgId;
    private Integer code;
    private String message;
}
