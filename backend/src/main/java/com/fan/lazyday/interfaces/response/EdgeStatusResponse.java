package com.fan.lazyday.interfaces.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EdgeStatusResponse {
    private boolean connected;
    private int sessionCount;
    private Long lastSeenAgoMs;
}
