package com.fan.lazyday.domain.webhookconfig;

import com.fan.lazyday.domain.webhookconfig.entity.WebhookConfigEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookConfigAggregationTest {

    @Test
    @DisplayName("创建配置时校验 https URL 并序列化事件类型")
    void create_shouldValidateAndSerializeEventTypes() {
        WebhookConfigAggregation aggregation = WebhookConfigAggregation.create(
                100L,
                "prod-hook",
                "https://example.com/webhook",
                List.of(WebhookEventType.APPKEY_DISABLED, WebhookEventType.QUOTA_EXCEEDED),
                "encrypted"
        );

        WebhookConfigEntity entity = aggregation.getWebhookConfigEntity();
        assertThat(entity.getDelegate().getTenantId()).isEqualTo(100L);
        assertThat(entity.getDelegate().getEventTypes()).isEqualTo("appkey.disabled,quota.exceeded");
        assertThat(entity.eventTypes()).containsExactly(WebhookEventType.APPKEY_DISABLED, WebhookEventType.QUOTA_EXCEEDED);
    }

    @Test
    @DisplayName("非 https URL 或空事件类型会被拒绝")
    void create_whenInvalid_shouldThrow() {
        assertThatThrownBy(() -> WebhookConfigAggregation.create(
                100L,
                "bad",
                "http://example.com/webhook",
                List.of(WebhookEventType.APPKEY_DISABLED),
                "encrypted"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");

        assertThatThrownBy(() -> WebhookConfigAggregation.create(
                100L,
                "bad",
                "https://example.com/webhook",
                List.of(),
                "encrypted"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventTypes");
    }
}
