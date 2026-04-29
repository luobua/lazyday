package com.fan.lazyday.infrastructure.scheduler;

import com.fan.lazyday.application.facade.impl.WebhookFacadeImpl;
import com.fan.lazyday.domain.event.WebhookPermanentFailedEvent;
import com.fan.lazyday.domain.webhookconfig.po.WebhookConfigPO;
import com.fan.lazyday.domain.webhookconfig.repository.WebhookConfigRepository;
import com.fan.lazyday.domain.webhookevent.po.WebhookEventPO;
import com.fan.lazyday.domain.webhookevent.repository.WebhookEventRepository;
import com.fan.lazyday.infrastructure.event.DomainEventPublisher;
import com.fan.lazyday.infrastructure.properties.ServiceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookDispatcherTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;
    @Mock
    private WebhookConfigRepository webhookConfigRepository;
    @Mock
    private DomainEventPublisher domainEventPublisher;

    private ServiceProperties serviceProperties;

    @BeforeEach
    void setUp() {
        serviceProperties = new ServiceProperties();
        serviceProperties.setEncryptionKey("test-encryption-key");
        serviceProperties.getWebhook().setMaxRetries(5);
        serviceProperties.getWebhook().setBackoffSequence("60,300,1800,7200,21600");
    }

    @Test
    @DisplayName("dispatch: 2xx 响应更新为 succeeded")
    void dispatch_success_shouldMarkSucceeded() {
        WebhookDispatcher dispatcher = dispatcher(request -> {
            assertThat(request.headers().getFirst("X-Lazyday-Event-Id")).isEqualTo("101");
            assertThat(request.headers().getFirst("X-Lazyday-Signature")).isNotBlank();
            return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
        });
        WebhookEventPO event = event(101L, 0);

        when(webhookEventRepository.recoverGhostLocks()).thenReturn(Mono.just(0L));
        when(webhookEventRepository.selectDueForDispatch(100)).thenReturn(Flux.just(event));
        when(webhookEventRepository.updateToDelivering(anyCollection(), any())).thenReturn(Mono.just(1L));
        when(webhookConfigRepository.findByIdAndTenantId(11L, 7L)).thenReturn(Mono.just(config()));
        when(webhookEventRepository.updateToSucceeded(101L, 204)).thenReturn(Mono.just(1L));

        StepVerifier.create(dispatcher.dispatchOnce())
                .verifyComplete();

        verify(webhookEventRepository).updateToSucceeded(101L, 204);
        verify(webhookEventRepository, never()).updateToFailedForRetry(any(), any(Integer.class), any(), any(), any(), any());
    }

    @Test
    @DisplayName("dispatch: 非 2xx 响应按退避序列回到 pending")
    void dispatch_httpFailure_shouldScheduleRetry() {
        WebhookDispatcher dispatcher = dispatcher(request -> Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("server error")
                .build()));
        WebhookEventPO event = event(101L, 0);

        when(webhookEventRepository.recoverGhostLocks()).thenReturn(Mono.just(0L));
        when(webhookEventRepository.selectDueForDispatch(100)).thenReturn(Flux.just(event));
        when(webhookEventRepository.updateToDelivering(anyCollection(), any())).thenReturn(Mono.just(1L));
        when(webhookConfigRepository.findByIdAndTenantId(11L, 7L)).thenReturn(Mono.just(config()));
        when(webhookEventRepository.updateToFailedForRetry(eq(101L), eq(1), any(), eq(500), eq("server error"), eq(null)))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(dispatcher.dispatchOnce())
                .verifyComplete();

        ArgumentCaptor<Instant> nextRetryCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(webhookEventRepository).updateToFailedForRetry(eq(101L), eq(1), nextRetryCaptor.capture(), eq(500), eq("server error"), eq(null));
        assertThat(nextRetryCaptor.getValue()).isAfter(Instant.now().plusSeconds(50));
    }

    @Test
    @DisplayName("dispatch: 达到最大重试后置 permanent_failed 并发布事件")
    void dispatch_maxRetryExceeded_shouldMarkPermanentFailedAndPublishEvent() {
        WebhookDispatcher dispatcher = dispatcher(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY)
                .body("bad gateway")
                .build()));
        WebhookEventPO event = event(101L, 4);

        when(webhookEventRepository.recoverGhostLocks()).thenReturn(Mono.just(0L));
        when(webhookEventRepository.selectDueForDispatch(100)).thenReturn(Flux.just(event));
        when(webhookEventRepository.updateToDelivering(anyCollection(), any())).thenReturn(Mono.just(1L));
        when(webhookConfigRepository.findByIdAndTenantId(11L, 7L)).thenReturn(Mono.just(config()));
        when(webhookEventRepository.updateToPermanentFailed(101L, 5, 502, "bad gateway", null)).thenReturn(Mono.just(1L));

        StepVerifier.create(dispatcher.dispatchOnce())
                .verifyComplete();

        ArgumentCaptor<WebhookPermanentFailedEvent> captor = ArgumentCaptor.forClass(WebhookPermanentFailedEvent.class);
        verify(domainEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(7L);
        assertThat(captor.getValue().eventId()).isEqualTo(101L);
        assertThat(captor.getValue().configId()).isEqualTo(11L);
        assertThat(captor.getValue().failedEventType()).isEqualTo("appkey.disabled");
    }

    private WebhookDispatcher dispatcher(ExchangeFunction exchange) {
        return new WebhookDispatcher(
                webhookEventRepository,
                webhookConfigRepository,
                domainEventPublisher,
                serviceProperties,
                WebClient.builder().exchangeFunction(exchange)
        );
    }

    private WebhookEventPO event(Long id, int retryCount) {
        return new WebhookEventPO()
                .setId(id)
                .setTenantId(7L)
                .setConfigId(11L)
                .setEventType("appkey.disabled")
                .setPayload("{\"ok\":true}")
                .setStatus("pending")
                .setRetryCount(retryCount)
                .setNextRetryAt(Instant.now());
    }

    private WebhookConfigPO config() {
        return new WebhookConfigPO()
                .setId(11L)
                .setTenantId(7L)
                .setName("prod")
                .setUrl("https://example.com/webhook")
                .setEventTypes("appkey.disabled")
                .setSecretEncrypted(WebhookFacadeImpl.encryptSecretForTest("plain-secret", "test-encryption-key"))
                .setStatus("ACTIVE");
    }
}
