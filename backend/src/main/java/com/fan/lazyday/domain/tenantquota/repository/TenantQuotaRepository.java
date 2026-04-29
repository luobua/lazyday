package com.fan.lazyday.domain.tenantquota.repository;

import com.fan.lazyday.domain.tenantquota.po.TenantQuota;
import com.fan.lazyday.infrastructure.helper.R2dbcHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static com.fan.lazyday.domain.tenantquota.entity.TenantQuotaEntity.PO_CLASS;
import static org.springframework.data.relational.core.query.Query.query;

@Component
@RequiredArgsConstructor
public class TenantQuotaRepository {
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    public Mono<TenantQuota> insert(TenantQuota tenantQuota) {
        return r2dbcEntityTemplate.insert(tenantQuota);
    }

    public Mono<TenantQuota> save(TenantQuota tenantQuota) {
        return insert(tenantQuota);
    }

    public Mono<TenantQuota> findByTenantId(Long tenantId) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(TenantQuota::getTenantId)).is(tenantId);
        return r2dbcEntityTemplate.selectOne(query(criteria), PO_CLASS);
    }

    public Mono<Long> updateByTenantId(Long tenantId, Update update) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(TenantQuota::getTenantId)).is(tenantId);
        return r2dbcEntityTemplate.update(PO_CLASS)
                .matching(query(criteria))
                .apply(update);
    }

    public Mono<Long> deleteByTenantId(Long tenantId) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(TenantQuota::getTenantId)).is(tenantId);
        return r2dbcEntityTemplate.delete(Query.query(criteria), PO_CLASS);
    }

    public Mono<Long> countByPlanId(Long planId) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(TenantQuota::getPlanId)).is(planId);
        return r2dbcEntityTemplate.count(Query.query(criteria), PO_CLASS);
    }
}
