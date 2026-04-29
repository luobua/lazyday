package com.fan.lazyday.domain.tenantquota.repository;

import com.fan.lazyday.domain.tenant.po.Tenant;
import com.fan.lazyday.domain.tenantquota.po.TenantQuota;
import com.fan.lazyday.support.PostgresSpringBootIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.relational.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;

class TenantQuotaRepositoryTest extends PostgresSpringBootIntegrationTestSupport {

    @Test
    @DisplayName("tenantId 隔离: 查询、更新、删除只影响目标租户")
    void tenantIsolation_shouldScopeOperationsByTenantId() {
        Tenant tenantA = createTenant("quota-a");
        Tenant tenantB = createTenant("quota-b");

        TenantQuota quotaA = bindTenantToPlan(tenantA, "Free");
        TenantQuota quotaB = bindTenantToPlan(tenantB, "Pro");

        assertThat(tenantQuotaRepository.findByTenantId(tenantA.getId()).block().getPlanId()).isEqualTo(quotaA.getPlanId());
        assertThat(tenantQuotaRepository.findByTenantId(tenantB.getId()).block().getPlanId()).isEqualTo(quotaB.getPlanId());

        tenantQuotaRepository.updateByTenantId(
                        tenantA.getId(),
                        Update.update("custom_qps_limit", 200).set("custom_daily_limit", 2000L)
                )
                .block();

        TenantQuota updatedA = tenantQuotaRepository.findByTenantId(tenantA.getId()).block();
        TenantQuota untouchedB = tenantQuotaRepository.findByTenantId(tenantB.getId()).block();

        assertThat(updatedA.getCustomQpsLimit()).isEqualTo(200);
        assertThat(updatedA.getCustomDailyLimit()).isEqualTo(2000L);
        assertThat(untouchedB.getCustomQpsLimit()).isNull();

        tenantQuotaRepository.deleteByTenantId(tenantA.getId()).block();

        assertThat(tenantQuotaRepository.findByTenantId(tenantA.getId()).block()).isNull();
        assertThat(tenantQuotaRepository.findByTenantId(tenantB.getId()).block()).isNotNull();
    }
}
