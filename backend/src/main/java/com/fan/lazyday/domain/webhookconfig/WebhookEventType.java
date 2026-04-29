package com.fan.lazyday.domain.webhookconfig;

import java.util.Arrays;

public enum WebhookEventType {
    APPKEY_DISABLED("appkey.disabled"),
    APPKEY_ROTATED("appkey.rotated"),
    TENANT_SUSPENDED("tenant.suspended"),
    TENANT_RESUMED("tenant.resumed"),
    QUOTA_EXCEEDED("quota.exceeded"),
    QUOTA_PLAN_CHANGED("quota.plan_changed");

    private final String value;

    WebhookEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static WebhookEventType fromValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported webhook event type: " + value));
    }
}
