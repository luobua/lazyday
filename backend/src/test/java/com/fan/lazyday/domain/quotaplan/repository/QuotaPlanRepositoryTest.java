package com.fan.lazyday.domain.quotaplan.repository;

import com.fan.lazyday.domain.quotaplan.po.QuotaPlan;
import com.fan.lazyday.support.PostgresSpringBootIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.relational.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;

class QuotaPlanRepositoryTest extends PostgresSpringBootIntegrationTestSupport {

    @Test
    @DisplayName("CRUD: 保存、按状态查询、按 id 更新")
    void crud_shouldPersistQueryAndUpdatePlan() {
        QuotaPlan plan = new QuotaPlan();
        plan.setName("Spec-Pro");
        plan.setQpsLimit(80);
        plan.setDailyLimit(80_000L);
        plan.setMonthlyLimit(800_000L);
        plan.setMaxAppKeys(20);
        plan.setStatus("ACTIVE");

        QuotaPlan saved = quotaPlanRepository.save(plan).block();
        QuotaPlan found = quotaPlanRepository.findById(saved.getId()).block();

        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Spec-Pro");
        assertThat(quotaPlanRepository.findByStatus("ACTIVE").collectList().block())
                .extracting(QuotaPlan::getName)
                .contains("Spec-Pro", "Free", "Pro", "Enterprise");

        quotaPlanRepository.updateById(saved.getId(), Update.update("qps_limit", 120).set("status", "DISABLED"))
                .block();

        QuotaPlan updated = quotaPlanRepository.findById(saved.getId()).block();
        assertThat(updated.getQpsLimit()).isEqualTo(120);
        assertThat(updated.getStatus()).isEqualTo("DISABLED");
    }
}
