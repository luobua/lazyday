package com.fan.lazyday.application.facade.impl;

import com.fan.lazyday.domain.webhookconfig.po.WebhookConfigPO;
import com.fan.lazyday.domain.webhookconfig.repository.WebhookConfigRepository;
import com.fan.lazyday.infrastructure.exception.BizException;
import com.fan.lazyday.infrastructure.properties.ServiceProperties;
import com.fan.lazyday.interfaces.request.CreateWebhookRequest;
import com.fan.lazyday.interfaces.request.UpdateWebhookRequest;
import com.fan.lazyday.interfaces.response.WebhookConfigResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.relational.core.query.Update;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookFacadeImplTest {

    @Mock
    private WebhookConfigRepository webhookConfigRepository;

    private ServiceProperties serviceProperties;

    @BeforeEach
    void setUp() {
        serviceProperties = new ServiceProperties();
        serviceProperties.setEncryptionKey("test-encryption-key");
    }

    @Test
    @DisplayName("create: 生成一次性 secret，密文入库且响应不暴露密文")
    void create_shouldGeneratePlainSecretOnceAndStoreEncryptedSecret() {
        WebhookFacadeImpl facade = facade(request -> Mono.error(new AssertionError("test push should not run")));

        CreateWebhookRequest request = new CreateWebhookRequest();
        request.setName("prod");
        request.setUrl("https://example.com/webhook");
        request.setEventTypes(List.of("appkey.disabled", "quota.exceeded"));

        when(webhookConfigRepository.insert(any(WebhookConfigPO.class))).thenAnswer(invocation -> {
            WebhookConfigPO po = invocation.getArgument(0);
            po.setId(11L);
            po.setCreateTime(Instant.parse("2026-04-29T00:00:00Z"));
            return Mono.just(po);
        });

        StepVerifier.create(facade.create(7L, request))
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo(11L);
                    assertThat(response.getName()).isEqualTo("prod");
                    assertThat(response.getEventTypes()).containsExactly("appkey.disabled", "quota.exceeded");
                    assertThat(response.getSecret()).startsWith("whsec_");
                    assertThat(response.getSecret()).hasSizeGreaterThan(32);
                    assertThat(response.getSecretEncrypted()).isNull();
                })
                .verifyComplete();

        ArgumentCaptor<WebhookConfigPO> captor = ArgumentCaptor.forClass(WebhookConfigPO.class);
        verify(webhookConfigRepository).insert(captor.capture());
        WebhookConfigPO stored = captor.getValue();
        assertThat(stored.getTenantId()).isEqualTo(7L);
        assertThat(stored.getStatus()).isEqualTo("ACTIVE");
        assertThat(stored.getSecretEncrypted()).isNotBlank();
        assertThat(stored.getSecretEncrypted()).doesNotStartWith("whsec_");
    }

    @Test
    @DisplayName("create: 拒绝非 https URL")
    void create_insecureUrl_shouldFail() {
        WebhookFacadeImpl facade = facade(request -> Mono.error(new AssertionError("test push should not run")));

        CreateWebhookRequest request = new CreateWebhookRequest();
        request.setName("dev");
        request.setUrl("http://example.com/webhook");
        request.setEventTypes(List.of("appkey.disabled"));

        StepVerifier.create(facade.create(7L, request))
                .expectErrorMatches(ex -> ex instanceof BizException be
                        && be.getErrorCode().equals("WEBHOOK_INSECURE_URL"))
                .verify();
    }

    @Test
    @DisplayName("create: 拒绝 localhost 或私网地址")
    void create_privateNetworkUrl_shouldFail() {
        WebhookFacadeImpl facade = facade(request -> Mono.error(new AssertionError("test push should not run")));

        CreateWebhookRequest request = new CreateWebhookRequest();
        request.setName("dev");
        request.setUrl("https://127.0.0.1/webhook");
        request.setEventTypes(List.of("appkey.disabled"));

        StepVerifier.create(facade.create(7L, request))
                .expectErrorMatches(ex -> ex instanceof BizException be
                        && be.getErrorCode().equals("WEBHOOK_PRIVATE_NETWORK_URL"))
                .verify();
    }

    @Test
    @DisplayName("get: 跨租户隔离返回 WEBHOOK_NOT_FOUND")
    void get_crossTenant_shouldReturnNotFound() {
        WebhookFacadeImpl facade = facade(request -> Mono.error(new AssertionError("test push should not run")));
        when(webhookConfigRepository.findByIdAndTenantId(99L, 7L)).thenReturn(Mono.empty());

        StepVerifier.create(facade.get(7L, 99L))
                .expectErrorMatches(ex -> ex instanceof BizException be
                        && be.getErrorCode().equals("WEBHOOK_NOT_FOUND"))
                .verify();
    }

    @Test
    @DisplayName("rotateSecret: 更新密文并只返回新明文 secret")
    void rotateSecret_shouldUpdateEncryptedSecretAndReturnPlainSecret() {
        WebhookFacadeImpl facade = facade(request -> Mono.error(new AssertionError("test push should not run")));
        WebhookConfigPO existing = po(11L, 7L);
        existing.setSecretEncrypted("old");

        when(webhookConfigRepository.findByIdAndTenantId(11L, 7L)).thenReturn(Mono.just(existing));
        when(webhookConfigRepository.updateByIdAndTenantId(eq(11L), eq(7L), any(Update.class))).thenReturn(Mono.just(1L));

        StepVerifier.create(facade.rotateSecret(7L, 11L))
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo(11L);
                    assertThat(response.getSecret()).startsWith("whsec_");
                    assertThat(response.getSecretEncrypted()).isNull();
                })
                .verifyComplete();

        verify(webhookConfigRepository).updateByIdAndTenantId(eq(11L), eq(7L), any(Update.class));
    }

    @Test
    @DisplayName("testPush: 使用配置 secret 发送探测并返回状态、头、体摘要")
    void testPush_shouldReturnHttpResult() {
        ExchangeFunction exchange = request -> {
            assertThat(request.headers().getFirst("X-Lazyday-Event-Type")).isEqualTo("webhook.test");
            assertThat(request.headers().getFirst("X-Lazyday-Signature")).isNotBlank();
            return Mono.just(ClientResponse.create(HttpStatus.ACCEPTED)
                    .header("X-Echo", "ok")
                    .body("accepted")
                    .build());
        };
        WebhookFacadeImpl facade = facade(exchange);
        WebhookConfigPO existing = po(11L, 7L);
        existing.setSecretEncrypted(WebhookFacadeImpl.encryptSecretForTest("plain-secret", "test-encryption-key"));

        when(webhookConfigRepository.findByIdAndTenantId(11L, 7L)).thenReturn(Mono.just(existing));

        StepVerifier.create(facade.testPush(7L, 11L))
                .assertNext(response -> {
                    assertThat(response.getHttpStatus()).isEqualTo(202);
                    assertThat(response.getResponseHeaders()).containsEntry("X-Echo", "ok");
                    assertThat(response.getResponseBodyExcerpt()).isEqualTo("accepted");
                    assertThat(response.getLatencyMs()).isGreaterThanOrEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("update: 只更新当前租户配置并返回更新后的 DTO")
    void update_shouldScopeByTenant() {
        WebhookFacadeImpl facade = facade(request -> Mono.error(new AssertionError("test push should not run")));
        WebhookConfigPO existing = po(11L, 7L);
        WebhookConfigPO updated = po(11L, 7L);
        updated.setName("new");
        updated.setUrl("https://example.com/new");
        updated.setEventTypes("tenant.suspended");
        updated.setStatus("DISABLED");

        UpdateWebhookRequest request = new UpdateWebhookRequest();
        request.setName("new");
        request.setUrl("https://example.com/new");
        request.setEventTypes(List.of("tenant.suspended"));
        request.setStatus("DISABLED");

        when(webhookConfigRepository.findByIdAndTenantId(11L, 7L)).thenReturn(Mono.just(existing), Mono.just(updated));
        when(webhookConfigRepository.updateByIdAndTenantId(eq(11L), eq(7L), any(Update.class))).thenReturn(Mono.just(1L));

        StepVerifier.create(facade.update(7L, 11L, request))
                .assertNext(response -> {
                    assertThat(response.getName()).isEqualTo("new");
                    assertThat(response.getUrl()).isEqualTo("https://example.com/new");
                    assertThat(response.getEventTypes()).containsExactly("tenant.suspended");
                    assertThat(response.getStatus()).isEqualTo("DISABLED");
                })
                .verifyComplete();
    }

    private WebhookFacadeImpl facade(ExchangeFunction exchange) {
        return new WebhookFacadeImpl(
                webhookConfigRepository,
                serviceProperties,
                WebClient.builder().exchangeFunction(exchange)
        );
    }

    private static WebhookConfigPO po(Long id, Long tenantId) {
        return new WebhookConfigPO()
                .setId(id)
                .setTenantId(tenantId)
                .setName("prod")
                .setUrl("https://example.com/webhook")
                .setEventTypes("appkey.disabled")
                .setSecretEncrypted("encrypted")
                .setStatus("ACTIVE");
    }
}
