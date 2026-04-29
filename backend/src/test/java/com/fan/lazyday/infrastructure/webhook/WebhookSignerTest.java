package com.fan.lazyday.infrastructure.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    @Test
    @DisplayName("使用 timestamp.body 计算 HMAC-SHA256 hex 签名")
    void sign_shouldReturnHmacSha256Hex() {
        String signature = WebhookSigner.sign("secret", "1777461600", "{\"event_type\":\"webhook.test\"}");

        assertThat(signature).isEqualTo("c55be14e9809c12d54ee2cadbbc9fec34b6890d739b3812bf153a96444beabd8");
    }
}
