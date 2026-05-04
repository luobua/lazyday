package com.fan.lazyday.domain.braindispatch.entity;

import java.util.Arrays;

public enum BrainDispatchStatus {
    PENDING("pending"),
    SENT("sent"),
    ACKED("acked"),
    FAILED("failed"),
    TIMEOUT("timeout");

    private final String value;

    BrainDispatchStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean isTerminal() {
        return this == ACKED || this == FAILED || this == TIMEOUT;
    }

    public static BrainDispatchStatus fromValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown brain dispatch status: " + value));
    }
}
