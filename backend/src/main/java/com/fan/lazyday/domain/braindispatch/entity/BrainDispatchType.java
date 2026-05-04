package com.fan.lazyday.domain.braindispatch.entity;

public enum BrainDispatchType {
    CONFIG_UPDATE,
    ACK,
    HEARTBEAT;

    public String value() {
        return name();
    }
}
