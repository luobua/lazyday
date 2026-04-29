package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.application.facade.WebhookFacade;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.infrastructure.security.TenantContext;
import com.fan.lazyday.interfaces.request.CreateWebhookRequest;
import com.fan.lazyday.interfaces.request.UpdateWebhookRequest;
import com.fan.lazyday.interfaces.response.WebhookConfigResponse;
import com.fan.lazyday.interfaces.response.WebhookTestResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortalWebhookHandlerTest {

    @Mock
    private WebhookFacade webhookFacade;

    private PortalWebhookHandler handler;

    private static final Long TENANT_ID = 100L;
    private static final Long WEBHOOK_ID = 11L;
    private static final Context TENANT_CTX = TenantContext.write(1L, TENANT_ID, "TENANT_ADMIN")
            .put(RequestIdFilter.REQUEST_ID_KEY, "req-id");

    @BeforeEach
    void setUp() {
        handler = new PortalWebhookHandler(webhookFacade);
    }

    @Test
    @DisplayName("list: 调用当前租户 facade")
    void list_success() {
        WebhookConfigResponse response = response();
        when(webhookFacade.list(TENANT_ID)).thenReturn(Mono.just(List.of(response)));

        StepVerifier.create(handler.list().contextWrite(TENANT_CTX))
                .assertNext(apiResponse -> {
                    assertThat(apiResponse.getCode()).isZero();
                    assertThat(apiResponse.getData()).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("create: 请求体透传到 facade")
    void create_success() {
        CreateWebhookRequest request = new CreateWebhookRequest();
        request.setName("prod");
        request.setUrl("https://example.com/webhook");
        request.setEventTypes(List.of("appkey.disabled"));

        when(webhookFacade.create(eq(TENANT_ID), eq(request))).thenReturn(Mono.just(response()));

        StepVerifier.create(handler.create(Mono.just(request)).contextWrite(TENANT_CTX))
                .assertNext(apiResponse -> assertThat(apiResponse.getData().getId()).isEqualTo(WEBHOOK_ID))
                .verifyComplete();
    }

    @Test
    @DisplayName("get/update/delete/rotate/test: 均按租户隔离调用 facade")
    void operations_success() {
        UpdateWebhookRequest updateRequest = new UpdateWebhookRequest();
        updateRequest.setStatus("DISABLED");
        WebhookTestResultResponse testResult = new WebhookTestResultResponse();
        testResult.setHttpStatus(204);

        when(webhookFacade.get(TENANT_ID, WEBHOOK_ID)).thenReturn(Mono.just(response()));
        when(webhookFacade.update(eq(TENANT_ID), eq(WEBHOOK_ID), eq(updateRequest))).thenReturn(Mono.just(response()));
        when(webhookFacade.delete(TENANT_ID, WEBHOOK_ID)).thenReturn(Mono.empty());
        when(webhookFacade.rotateSecret(TENANT_ID, WEBHOOK_ID)).thenReturn(Mono.just(response()));
        when(webhookFacade.testPush(TENANT_ID, WEBHOOK_ID)).thenReturn(Mono.just(testResult));

        StepVerifier.create(handler.get(WEBHOOK_ID).contextWrite(TENANT_CTX))
                .assertNext(apiResponse -> assertThat(apiResponse.getData().getId()).isEqualTo(WEBHOOK_ID))
                .verifyComplete();
        StepVerifier.create(handler.update(WEBHOOK_ID, Mono.just(updateRequest)).contextWrite(TENANT_CTX))
                .assertNext(apiResponse -> assertThat(apiResponse.getCode()).isZero())
                .verifyComplete();
        StepVerifier.create(handler.delete(WEBHOOK_ID).contextWrite(TENANT_CTX))
                .assertNext(apiResponse -> assertThat(apiResponse.getCode()).isZero())
                .verifyComplete();
        StepVerifier.create(handler.rotateSecret(WEBHOOK_ID).contextWrite(TENANT_CTX))
                .assertNext(apiResponse -> assertThat(apiResponse.getData().getId()).isEqualTo(WEBHOOK_ID))
                .verifyComplete();
        StepVerifier.create(handler.test(WEBHOOK_ID).contextWrite(TENANT_CTX))
                .assertNext(apiResponse -> assertThat(apiResponse.getData().getHttpStatus()).isEqualTo(204))
                .verifyComplete();
    }

    private WebhookConfigResponse response() {
        WebhookConfigResponse response = new WebhookConfigResponse();
        response.setId(WEBHOOK_ID);
        response.setName("prod");
        response.setUrl("https://example.com/webhook");
        response.setEventTypes(List.of("appkey.disabled"));
        response.setStatus("ACTIVE");
        return response;
    }
}
