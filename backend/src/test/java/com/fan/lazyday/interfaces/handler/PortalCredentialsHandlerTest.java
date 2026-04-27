package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.application.facade.CredentialsFacade;
import com.fan.lazyday.infrastructure.exception.BizException;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.infrastructure.security.TenantContext;
import com.fan.lazyday.interfaces.request.CreateAppKeyRequest;
import com.fan.lazyday.interfaces.response.AppKeyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * PortalCredentialsHandler 单元测试
 * 覆盖：list/create/disable/enable/rotateSecret/delete
 */
@ExtendWith(MockitoExtension.class)
class PortalCredentialsHandlerTest {

    @Mock private CredentialsFacade credentialsFacade;

    private PortalCredentialsHandler handler;

    private static final Long USER_ID =55555555L;
    private static final Long TENANT_ID = 100L;
    private static final Long APP_KEY_ID = 1L;
    private static final Context TENANT_CTX = TenantContext.write(USER_ID, TENANT_ID, "TENANT_ADMIN")
            .put(RequestIdFilter.REQUEST_ID_KEY, "req-id");

    @BeforeEach
    void setUp() {
        handler = new PortalCredentialsHandler(credentialsFacade);
    }

    private AppKeyResponse buildAppKeyResponse() {
        AppKeyResponse resp = new AppKeyResponse();
        resp.setId(APP_KEY_ID);
        resp.setName("test-app");
        resp.setAppKey("ak_test1234567890123456789012345678");
        resp.setStatus("ACTIVE");
        resp.setScopes("read,write");
        resp.setCreateTime(Instant.now());
        return resp;
    }

    @Nested
    @DisplayName("list")
    class List {

        @Test
        @DisplayName("列出所有 AppKey")
        void list_success() {
            AppKeyResponse resp = buildAppKeyResponse();
            when(credentialsFacade.list(TENANT_ID)).thenReturn(Mono.just(java.util.List.of(resp)));

            StepVerifier.create(handler.list().contextWrite(TENANT_CTX))
                    .assertNext(response -> {
                        assertThat(response.getCode()).isEqualTo(0);
                        assertThat(response.getData()).hasSize(1);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("创建 AppKey 成功")
        void create_success() {
            AppKeyResponse resp = buildAppKeyResponse();
            resp.setAppKey("ak_new_key");
            resp.setSecretKey("sk_new_secret");
            when(credentialsFacade.create(eq(TENANT_ID), eq("my-app"), eq("read")))
                    .thenReturn(Mono.just(resp));

            CreateAppKeyRequest req = new CreateAppKeyRequest();
            req.setName("my-app");
            req.setScopes("read");

            StepVerifier.create(handler.create(Mono.just(req)).contextWrite(TENANT_CTX))
                    .assertNext(response -> {
                        assertThat(response.getCode()).isEqualTo(0);
                        assertThat(response.getData().getAppKey()).isEqualTo("ak_new_key");
                        assertThat(response.getData().getSecretKey()).isEqualTo("sk_new_secret");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("disable")
    class Disable {

        @Test
        @DisplayName("禁用 AppKey 成功")
        void disable_success() {
            when(credentialsFacade.disable(TENANT_ID, APP_KEY_ID)).thenReturn(Mono.empty());

            StepVerifier.create(handler.disable(APP_KEY_ID).contextWrite(TENANT_CTX))
                    .assertNext(response -> assertThat(response.getCode()).isEqualTo(0))
                    .verifyComplete();
        }

        @Test
        @DisplayName("无权禁用 → 错误传播")
        void disable_forbidden_shouldPropagateError() {
            when(credentialsFacade.disable(TENANT_ID, APP_KEY_ID))
                    .thenReturn(Mono.error(BizException.forbidden("FORBIDDEN_TENANT", "无权操作")));

            StepVerifier.create(handler.disable(APP_KEY_ID).contextWrite(TENANT_CTX))
                    .expectError(BizException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("enable")
    class Enable {

        @Test
        @DisplayName("启用 AppKey 成功")
        void enable_success() {
            when(credentialsFacade.enable(TENANT_ID, APP_KEY_ID)).thenReturn(Mono.empty());

            StepVerifier.create(handler.enable(APP_KEY_ID).contextWrite(TENANT_CTX))
                    .assertNext(response -> assertThat(response.getCode()).isEqualTo(0))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("rotateSecret")
    class RotateSecret {

        @Test
        @DisplayName("轮换密钥成功")
        void rotateSecret_success() {
            AppKeyResponse resp = buildAppKeyResponse();
            resp.setSecretKey("sk_rotated_new");
            when(credentialsFacade.rotateSecret(TENANT_ID, APP_KEY_ID)).thenReturn(Mono.just(resp));

            StepVerifier.create(handler.rotateSecret(APP_KEY_ID).contextWrite(TENANT_CTX))
                    .assertNext(response -> {
                        assertThat(response.getCode()).isEqualTo(0);
                        assertThat(response.getData().getSecretKey()).isEqualTo("sk_rotated_new");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("删除 AppKey 成功")
        void delete_success() {
            when(credentialsFacade.delete(TENANT_ID, APP_KEY_ID)).thenReturn(Mono.empty());

            StepVerifier.create(handler.delete(APP_KEY_ID).contextWrite(TENANT_CTX))
                    .assertNext(response -> assertThat(response.getCode()).isEqualTo(0))
                    .verifyComplete();
        }
    }
}
