package com.fan.lazyday.application.service;

import com.fan.lazyday.domain.event.QuotaExceededEvent;
import com.fan.lazyday.domain.event.WebhookPermanentFailedEvent;
import com.fan.lazyday.domain.tenant.po.Tenant;
import com.fan.lazyday.domain.tenant.repository.TenantRepository;
import com.fan.lazyday.domain.user.repository.UserRepository;
import com.fan.lazyday.domain.webhookconfig.po.WebhookConfigPO;
import com.fan.lazyday.domain.webhookconfig.repository.WebhookConfigRepository;
import com.fan.lazyday.infrastructure.event.DomainEventDeduplicator;
import com.fan.lazyday.infrastructure.event.DomainEventPublisher;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailSubscriberTest {

    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private WebhookConfigRepository webhookConfigRepository;
    @Mock
    private EmailService emailService;

    @Test
    @DisplayName("QuotaExceededEmailSubscriber: 首次事件发给所有 TENANT_ADMIN，24h 内去重")
    void quotaExceeded_shouldSendOnceToTenantAdmins() {
        QuotaExceededEmailSubscriber subscriber = new QuotaExceededEmailSubscriber(
                domainEventPublisher,
                emailService,
                userRepository,
                tenantRepository,
                new DomainEventDeduplicator()
        );
        QuotaExceededEvent event = new QuotaExceededEvent(7L, "day", 1000L, Instant.parse("2026-04-29T00:00:00Z"));

        when(userRepository.findTenantAdminEmailsByTenantId(7L)).thenReturn(Flux.just("a@example.com", "b@example.com"));
        when(tenantRepository.findById(7L)).thenReturn(Mono.just(tenant()));
        when(emailService.send(any(), eq("您的 Lazyday 配额已用尽"), eq("quota-exceeded"), any())).thenReturn(Mono.empty());

        StepVerifier.create(subscriber.handle(event)).verifyComplete();
        StepVerifier.create(subscriber.handle(event)).verifyComplete();

        ArgumentCaptor<List<String>> recipients = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Map<String, Object>> model = ArgumentCaptor.forClass(Map.class);
        verify(emailService, times(1)).send(recipients.capture(), eq("您的 Lazyday 配额已用尽"), eq("quota-exceeded"), model.capture());
        assertThat(recipients.getValue()).containsExactly("a@example.com", "b@example.com");
        assertThat(model.getValue()).containsEntry("tenantName", "Acme");
        assertThat(model.getValue()).containsEntry("period", "day");
        assertThat(model.getValue()).containsEntry("limit", 1000L);
    }

    @Test
    @DisplayName("QuotaExceededEmailSubscriber: 没有 admin 邮箱时跳过")
    void quotaExceeded_noAdmin_shouldSkip() {
        QuotaExceededEmailSubscriber subscriber = new QuotaExceededEmailSubscriber(
                domainEventPublisher,
                emailService,
                userRepository,
                tenantRepository,
                new DomainEventDeduplicator()
        );

        when(userRepository.findTenantAdminEmailsByTenantId(7L)).thenReturn(Flux.empty());

        StepVerifier.create(subscriber.handle(new QuotaExceededEvent(7L, "month", 5000L, Instant.now())))
                .verifyComplete();

        verify(emailService, never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("WebhookPermanentFailedEmailSubscriber: 首次永久失败发邮件，按 config 24h 去重")
    void webhookPermanentFailed_shouldSendOncePerConfig() {
        WebhookPermanentFailedEmailSubscriber subscriber = new WebhookPermanentFailedEmailSubscriber(
                domainEventPublisher,
                emailService,
                userRepository,
                tenantRepository,
                webhookConfigRepository,
                new DomainEventDeduplicator()
        );
        WebhookPermanentFailedEvent event = new WebhookPermanentFailedEvent(
                7L,
                101L,
                11L,
                "appkey.disabled",
                500,
                "server error",
                Instant.now()
        );

        when(userRepository.findTenantAdminEmailsByTenantId(7L)).thenReturn(Flux.just("admin@example.com"));
        when(tenantRepository.findById(7L)).thenReturn(Mono.just(tenant()));
        when(webhookConfigRepository.findByIdAndTenantId(11L, 7L)).thenReturn(Mono.just(webhookConfig()));
        when(emailService.send(any(), eq("Webhook 推送已永久失败 - prod-hook"), eq("webhook-permanent-failed"), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(subscriber.handle(event)).verifyComplete();
        StepVerifier.create(subscriber.handle(event)).verifyComplete();

        ArgumentCaptor<Map<String, Object>> model = ArgumentCaptor.forClass(Map.class);
        verify(emailService, times(1)).send(eq(List.of("admin@example.com")),
                eq("Webhook 推送已永久失败 - prod-hook"),
                eq("webhook-permanent-failed"),
                model.capture());
        assertThat(model.getValue()).containsEntry("webhookName", "prod-hook");
        assertThat(model.getValue()).containsEntry("eventType", "appkey.disabled");
        assertThat(model.getValue()).containsEntry("eventId", 101L);
    }

    private Tenant tenant() {
        return new Tenant()
                .setId(7L)
                .setName("Acme")
                .setContactEmail("admin@example.com")
                .setStatus("ACTIVE");
    }

    private WebhookConfigPO webhookConfig() {
        return new WebhookConfigPO()
                .setId(11L)
                .setTenantId(7L)
                .setName("prod-hook")
                .setUrl("https://example.com/webhook")
                .setStatus("ACTIVE");
    }
}
