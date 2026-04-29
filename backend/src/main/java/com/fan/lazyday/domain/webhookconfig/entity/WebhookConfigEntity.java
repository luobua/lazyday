package com.fan.lazyday.domain.webhookconfig.entity;

import com.fan.lazyday.domain.webhookconfig.WebhookEventType;
import com.fan.lazyday.domain.webhookconfig.po.WebhookConfigPO;
import com.fan.lazyday.infrastructure.domain.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

@Getter
public class WebhookConfigEntity extends Entity<Long> {

    public static final Class<WebhookConfigPO> PO_CLASS = WebhookConfigPO.class;

    @Setter(AccessLevel.PROTECTED)
    private WebhookConfigPO delegate;

    public static WebhookConfigEntity fromPo(WebhookConfigPO po) {
        WebhookConfigEntity entity = new WebhookConfigEntity();
        entity.setDelegate(po);
        return entity;
    }

    @Override
    public Long getId() {
        return delegate == null ? null : delegate.getId();
    }

    public List<WebhookEventType> eventTypes() {
        if (delegate.getEventTypes() == null || delegate.getEventTypes().isBlank()) {
            return List.of();
        }
        return Arrays.stream(delegate.getEventTypes().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(WebhookEventType::fromValue)
                .toList();
    }
}
