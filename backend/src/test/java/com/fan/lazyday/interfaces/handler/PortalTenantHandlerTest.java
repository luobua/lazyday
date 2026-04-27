package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.domain.tenant.po.Tenant;
import com.fan.lazyday.domain.tenant.repository.TenantRepository;
import com.fan.lazyday.infrastructure.exception.BizException;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.infrastructure.security.TenantContext;
import com.fan.lazyday.interfaces.request.UpdateTenantRequest;
import com.fan.lazyday.interfaces.response.TenantResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.relational.core.query.Update;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PortalTenantHandler 单元测试
 * 覆盖：getTenant/updateTenant
 */
@ExtendWith(MockitoExtension.class)
class PortalTenantHandlerTest {

    @Mock private TenantRepository tenantRepository;

    private PortalTenantHandler handler;

    private static final Long USER_ID = 66666666L;
    private static final Long TENANT_ID = 100L;
    private static final Context TENANT_CTX = TenantContext.write(USER_ID, TENANT_ID, "TENANT_ADMIN")
            .put(RequestIdFilter.REQUEST_ID_KEY, "req-id");

    @BeforeEach
    void setUp() {
        handler = new PortalTenantHandler(tenantRepository);
    }

    private Tenant buildTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setName("MyTenant");
        tenant.setStatus("ACTIVE");
        tenant.setPlanType("FREE");
        tenant.setContactEmail("admin@test.com");
        return tenant;
    }

    @Nested
    @DisplayName("getTenant")
    class GetTenant {

        @Test
        @DisplayName("获取租户信息成功")
        void getTenant_found_shouldReturnResponse() {
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Mono.just(buildTenant()));

            StepVerifier.create(handler.getTenant().contextWrite(TENANT_CTX))
                    .assertNext(response -> {
                        assertThat(response.getCode()).isEqualTo(0);
                        TenantResponse data = response.getData();
                        assertThat(data.getId()).isEqualTo(TENANT_ID);
                        assertThat(data.getName()).isEqualTo("MyTenant");
                        assertThat(data.getStatus()).isEqualTo("ACTIVE");
                        assertThat(data.getPlanType()).isEqualTo("FREE");
                        assertThat(data.getContactEmail()).isEqualTo("admin@test.com");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("租户不存在 → 抛 NOT_FOUND")
        void getTenant_notFound_shouldThrowNotFound() {
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Mono.empty());

            StepVerifier.create(handler.getTenant().contextWrite(TENANT_CTX))
                    .expectErrorMatches(ex ->
                            ex instanceof BizException be
                                    && be.getErrorCode().equals("TENANT_NOT_FOUND")
                                    && be.getMessage().equals("租户不存在")
                    )
                    .verify();
        }
    }

    @Nested
    @DisplayName("updateTenant")
    class UpdateTenant {

        @Test
        @DisplayName("更新租户信息成功")
        void updateTenant_success_shouldReturnUpdatedTenant() {
            Tenant updated = buildTenant();
            updated.setName("NewName");
            updated.setContactEmail("new@test.com");

            when(tenantRepository.update(eq(TENANT_ID), any(Update.class))).thenReturn(Mono.just(1L));
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Mono.just(updated));

            UpdateTenantRequest req = new UpdateTenantRequest();
            req.setName("NewName");
            req.setContactEmail("new@test.com");

            StepVerifier.create(handler.updateTenant(Mono.just(req)).contextWrite(TENANT_CTX))
                    .assertNext(response -> {
                        assertThat(response.getCode()).isEqualTo(0);
                        TenantResponse data = response.getData();
                        assertThat(data.getName()).isEqualTo("NewName");
                        assertThat(data.getContactEmail()).isEqualTo("new@test.com");
                    })
                    .verifyComplete();

            ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
            verify(tenantRepository).update(eq(TENANT_ID), captor.capture());
        }
    }
}
