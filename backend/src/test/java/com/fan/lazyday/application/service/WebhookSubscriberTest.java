package com.fan.lazyday.application.service;

import com.fan.lazyday.domain.event.AppKeyDisabledEvent;
import com.fan.lazyday.domain.webhookconfig.po.WebhookConfigPO;
import com.fan.lazyday.domain.webhookconfig.repository.WebhookConfigRepository;
import com.fan.lazyday.domain.webhookevent.po.WebhookEventPO;
import com.fan.lazyday.domain.webhookevent.repository.WebhookEventRepository;
import com.fan.lazyday.infrastructure.event.DomainEventPublisher;
import com.fan.lazyday.infrastructure.utils.JsonUtils;
import com.fan.lazyday.infrastructure.utils.id.SnowflakeIdWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookSubscriberTest {

    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private WebhookConfigRepository webhookConfigRepository;
    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Test
    @DisplayName("materialize: 每个匹配 active config 写一条 pending webhook event")
    void materialize_shouldFanOutToMatchingConfigs() {
        WebhookSubscriber subscriber = subscriber();
        AppKeyDisabledEvent event = new AppKeyDisabledEvent(
                7L,
                99L,
                "ak_test",
                Instant.parse("2026-04-29T00:00:00Z")
        );

        when(webhookConfigRepository.findActiveByTenantIdAndEventType(7L, "appkey.disabled"))
                .thenReturn(Flux.just(config(11L), config(12L)));
        when(webhookEventRepository.insert(any(WebhookEventPO.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(subscriber.materialize(event))
                .verifyComplete();

        ArgumentCaptor<WebhookEventPO> captor = ArgumentCaptor.forClass(WebhookEventPO.class);
        verify(webhookEventRepository, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(WebhookEventPO::getConfigId).containsExactly(11L, 12L);
        WebhookEventPO first = captor.getAllValues().getFirst();
        assertThat(first.getTenantId()).isEqualTo(7L);
        assertThat(first.getEventType()).isEqualTo("appkey.disabled");
        assertThat(first.getStatus()).isEqualTo("pending");
        assertThat(first.getRetryCount()).isZero();
        assertThat(first.getNextRetryAt()).isNotNull();
        assertThat(first.getCreatedTime()).isNotNull();

        Map<String, Object> payload = JsonUtils.parseAsMap(first.getPayload());
        assertThat(payload).containsEntry("event_type", "appkey.disabled");
        assertThat(payload).containsEntry("tenant_id", 7);
        assertThat(payload).containsEntry("event_time", "2026-04-29T00:00:00Z");
        assertThat(payload.get("data")).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("materialize: 没有订阅配置时不写事件表")
    void materialize_noMatchingConfig_shouldSkipInsert() {
        WebhookSubscriber subscriber = subscriber();
        AppKeyDisabledEvent event = new AppKeyDisabledEvent(
                7L,
                99L,
                "ak_test",
                Instant.parse("2026-04-29T00:00:00Z")
        );

        when(webhookConfigRepository.findActiveByTenantIdAndEventType(7L, "appkey.disabled"))
                .thenReturn(Flux.empty());

        StepVerifier.create(subscriber.materialize(event))
                .verifyComplete();

        verify(webhookEventRepository, never()).insert(any());
    }

    private WebhookSubscriber subscriber() {
        return new WebhookSubscriber(
                domainEventPublisher,
                webhookConfigRepository,
                webhookEventRepository,
                new SnowflakeIdWorker(1, 1)
        );
    }

    private WebhookConfigPO config(Long id) {
        return new WebhookConfigPO()
                .setId(id)
                .setTenantId(7L)
                .setName("prod")
                .setUrl("https://example.com/webhook")
                .setEventTypes("appkey.disabled")
                .setSecretEncrypted("secret")
                .setStatus("ACTIVE");
    }
}
