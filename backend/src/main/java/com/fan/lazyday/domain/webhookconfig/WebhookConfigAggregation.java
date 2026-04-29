package com.fan.lazyday.domain.webhookconfig;

import com.fan.lazyday.domain.webhookconfig.entity.WebhookConfigEntity;
import com.fan.lazyday.domain.webhookconfig.po.WebhookConfigPO;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.stream.Collectors;

@Getter
public class WebhookConfigAggregation {

    @Setter
    private WebhookConfigEntity webhookConfigEntity;

    public Long getId() {
        return webhookConfigEntity == null ? null : webhookConfigEntity.getId();
    }

    public static WebhookConfigAggregation create(Long tenantId,
                                                  String name,
                                                  String url,
                                                  Collection<WebhookEventType> eventTypes,
                                                  String secretEncrypted) {
        validateUrl(url);
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new IllegalArgumentException("eventTypes must not be empty");
        }
        WebhookConfigPO po = new WebhookConfigPO()
                .setTenantId(tenantId)
                .setName(name)
                .setUrl(url)
                .setEventTypes(serializeEventTypes(eventTypes))
                .setSecretEncrypted(secretEncrypted)
                .setStatus("ACTIVE");
        WebhookConfigAggregation aggregation = new WebhookConfigAggregation();
        aggregation.setWebhookConfigEntity(WebhookConfigEntity.fromPo(po));
        return aggregation;
    }

    public static String serializeEventTypes(Collection<WebhookEventType> eventTypes) {
        return eventTypes.stream()
                .map(WebhookEventType::value)
                .collect(Collectors.joining(","));
    }

    private static void validateUrl(String url) {
        if (url == null || !url.startsWith("https://")) {
            throw new IllegalArgumentException("webhook url must start with https://");
        }
    }
}
