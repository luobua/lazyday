package com.fan.lazyday.application.facade.impl;

import com.fan.lazyday.domain.event.AppKeyDisabledEvent;
import com.fan.lazyday.domain.event.AppKeyRotatedEvent;
import com.fan.lazyday.domain.event.DomainEvent;
import com.fan.lazyday.domain.appkey.po.AppKey;
import com.fan.lazyday.domain.appkey.repository.AppKeyRepository;
import com.fan.lazyday.infrastructure.exception.BizException;
import com.fan.lazyday.infrastructure.event.DomainEventPublisher;
import com.fan.lazyday.infrastructure.properties.ServiceProperties;
import com.fan.lazyday.interfaces.response.AppKeyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.relational.core.query.Update;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CredentialsFacadeImpl 单元测试
 * 覆盖：list/create/disable/enable/rotateSecret/delete
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CredentialsFacadeImplTest {

    @Mock private AppKeyRepository appKeyRepository;
    @Mock private ServiceProperties serviceProperties;
    @Mock private TransactionalOperator transactionalOperator;
    @Mock private DomainEventPublisher domainEventPublisher;

    private CredentialsFacadeImpl facade;

    private static final Long TENANT_ID = 100L;
    private static final Long APP_KEY_ID = 1L;
    private static final String ENCRYPTION_KEY = "test-encryption-key-12345";

    @BeforeEach
    void setUp() {
        facade = new CredentialsFacadeImpl(appKeyRepository, serviceProperties, transactionalOperator, domainEventPublisher);
        when(serviceProperties.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(domainEventPublisher.publish(any(DomainEvent.class))).thenReturn(Sinks.EmitResult.OK);
    }

    private AppKey buildAppKey() {
        AppKey po = new AppKey();
        po.setId(APP_KEY_ID);
        po.setTenantId(TENANT_ID);
        po.setName("test-app");
        po.setAppKey("ak_test1234567890123456789012345678");
        po.setSecretKeyEncrypted("encrypted_secret");
        po.setStatus("ACTIVE");
        po.setScopes("read,write");
        po.setCreateTime(Instant.now());
        return po;
    }

    @Nested
    @DisplayName("list")
    class List {

        @Test
        @DisplayName("列出所有 AppKey（密钥应被遮掩）")
        void list_shouldMaskSecretKeys() {
            AppKey appKey = buildAppKey();
            when(appKeyRepository.findByTenantId(TENANT_ID)).thenReturn(Flux.just(appKey));

            StepVerifier.create(facade.list(TENANT_ID))
                    .assertNext(list -> {
                        assertThat(list).hasSize(1);
                        AppKeyResponse resp = list.get(0);
                        assertThat(resp.getAppKey()).isEqualTo("ak_test****5678"); // 前7 + **** + 后4
                        assertThat(resp.getSecretKey()).isNull(); // list 不返回 secretKey
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("无 AppKey → 返回空列表")
        void list_noKeys_shouldReturnEmptyList() {
            when(appKeyRepository.findByTenantId(TENANT_ID)).thenReturn(Flux.empty());

            StepVerifier.create(facade.list(TENANT_ID))
                    .assertNext(list -> assertThat(list).isEmpty())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("创建成功 → 返回含明文密钥的响应")
        void create_success_shouldReturnPlainKeys() {
            when(appKeyRepository.insert(any(AppKey.class)))
                    .thenAnswer(inv -> {
                        AppKey po = inv.getArgument(0);
                        po.setId(APP_KEY_ID);
                        po.setCreateTime(Instant.now());
                        return Mono.just(po);
                    });

            StepVerifier.create(facade.create(TENANT_ID, "my-app", "read,write"))
                    .assertNext(response -> {
                        assertThat(response.getName()).isEqualTo("my-app");
                        assertThat(response.getAppKey()).startsWith("ak_");
                        assertThat(response.getSecretKey()).startsWith("sk_");
                        // create 时应返回明文密钥
                        assertThat(response.getAppKey().length()).isGreaterThan(10);
                    })
                    .verifyComplete();

            ArgumentCaptor<AppKey> captor = ArgumentCaptor.forClass(AppKey.class);
            verify(appKeyRepository).insert(captor.capture());
            AppKey saved = captor.getValue();
            assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(saved.getName()).isEqualTo("my-app");
            assertThat(saved.getScopes()).isEqualTo("read,write");
            assertThat(saved.getStatus()).isEqualTo("ACTIVE");
            // 加密后的密钥应存在
            assertThat(saved.getSecretKeyEncrypted()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("disable")
    class Disable {

        @Test
        @DisplayName("禁用成功")
        void disable_found_shouldDisable() {
            AppKey appKey = buildAppKey();
            when(appKeyRepository.findByIdAndTenantId(APP_KEY_ID, TENANT_ID)).thenReturn(Mono.just(appKey));
            when(appKeyRepository.updateByIdAndTenantId(eq(APP_KEY_ID), eq(TENANT_ID), any(Update.class)))
                    .thenReturn(Mono.just(1L));

            StepVerifier.create(facade.disable(TENANT_ID, APP_KEY_ID))
                    .verifyComplete();

            ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
            verify(appKeyRepository).updateByIdAndTenantId(eq(APP_KEY_ID), eq(TENANT_ID), captor.capture());
            ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
            verify(domainEventPublisher).publish(eventCaptor.capture());
            assertThat(eventCaptor.getValue()).isInstanceOf(AppKeyDisabledEvent.class);
            AppKeyDisabledEvent event = (AppKeyDisabledEvent) eventCaptor.getValue();
            assertThat(event.tenantId()).isEqualTo(TENANT_ID);
            assertThat(event.appKeyId()).isEqualTo(APP_KEY_ID);
            assertThat(event.appKeyValue()).isEqualTo("ak_test1234567890123456789012345678");
        }

        @Test
        @DisplayName("AppKey 不属于当前租户 → 抛 FORBIDDEN")
        void disable_wrongTenant_shouldThrowForbidden() {
            when(appKeyRepository.findByIdAndTenantId(APP_KEY_ID, TENANT_ID)).thenReturn(Mono.empty());

            StepVerifier.create(facade.disable(TENANT_ID, APP_KEY_ID))
                    .expectErrorMatches(ex ->
                            ex instanceof BizException be
                                    && be.getErrorCode().equals("FORBIDDEN_TENANT")
                    )
                    .verify();
        }
    }

    @Nested
    @DisplayName("enable")
    class Enable {

        @Test
        @DisplayName("启用成功")
        void enable_found_shouldEnable() {
            AppKey appKey = buildAppKey();
            appKey.setStatus("DISABLED");
            when(appKeyRepository.findByIdAndTenantId(APP_KEY_ID, TENANT_ID)).thenReturn(Mono.just(appKey));
            when(appKeyRepository.updateByIdAndTenantId(eq(APP_KEY_ID), eq(TENANT_ID), any(Update.class)))
                    .thenReturn(Mono.just(1L));

            StepVerifier.create(facade.enable(TENANT_ID, APP_KEY_ID))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("rotateSecret")
    class RotateSecret {

        @Test
        @DisplayName("轮换成功 → 返回新密钥")
        void rotateSecret_success_shouldReturnNewSecret() {
            AppKey appKey = buildAppKey();
            when(appKeyRepository.findByIdAndTenantId(APP_KEY_ID, TENANT_ID)).thenReturn(Mono.just(appKey));
            when(appKeyRepository.updateByIdAndTenantId(eq(APP_KEY_ID), eq(TENANT_ID), any(Update.class)))
                    .thenReturn(Mono.just(1L));

            StepVerifier.create(facade.rotateSecret(TENANT_ID, APP_KEY_ID))
                    .assertNext(response -> {
                        assertThat(response.getSecretKey()).startsWith("sk_");
                        // list 接口的遮掩密钥
                        assertThat(response.getAppKey()).isEqualTo("ak_test****5678");
                    })
                    .verifyComplete();

            ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
            verify(domainEventPublisher).publish(eventCaptor.capture());
            assertThat(eventCaptor.getValue()).isInstanceOf(AppKeyRotatedEvent.class);
            AppKeyRotatedEvent event = (AppKeyRotatedEvent) eventCaptor.getValue();
            assertThat(event.tenantId()).isEqualTo(TENANT_ID);
            assertThat(event.appKeyId()).isEqualTo(APP_KEY_ID);
            assertThat(event.previousSecretGraceUntil()).isNotNull();
        }

        @Test
        @DisplayName("AppKey 不属于当前租户 → 抛 FORBIDDEN")
        void rotateSecret_wrongTenant_shouldThrowForbidden() {
            when(appKeyRepository.findByIdAndTenantId(APP_KEY_ID, TENANT_ID)).thenReturn(Mono.empty());

            StepVerifier.create(facade.rotateSecret(TENANT_ID, APP_KEY_ID))
                    .expectErrorMatches(ex ->
                            ex instanceof BizException be
                                    && be.getErrorCode().equals("FORBIDDEN_TENANT")
                    )
                    .verify();
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("删除成功")
        void delete_found_shouldDelete() {
            AppKey appKey = buildAppKey();
            when(appKeyRepository.findByIdAndTenantId(APP_KEY_ID, TENANT_ID)).thenReturn(Mono.just(appKey));
            when(appKeyRepository.deleteByIdAndTenantId(APP_KEY_ID, TENANT_ID)).thenReturn(Mono.just(1L));

            StepVerifier.create(facade.delete(TENANT_ID, APP_KEY_ID))
                    .verifyComplete();
        }

        @Test
        @DisplayName("AppKey 不属于当前租户 → 抛 FORBIDDEN")
        void delete_wrongTenant_shouldThrowForbidden() {
            when(appKeyRepository.findByIdAndTenantId(APP_KEY_ID, TENANT_ID)).thenReturn(Mono.empty());

            StepVerifier.create(facade.delete(TENANT_ID, APP_KEY_ID))
                    .expectErrorMatches(ex ->
                            ex instanceof BizException be
                                    && be.getErrorCode().equals("FORBIDDEN_TENANT")
                    )
                    .verify();
        }
    }
}
