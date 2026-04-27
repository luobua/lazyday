package com.fan.lazyday.domain.tenant.repository;

import com.fan.lazyday.domain.tenant.po.Tenant;
import com.fan.lazyday.infrastructure.helper.R2dbcHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static com.fan.lazyday.domain.tenant.entity.TenantEntity.PO_CLASS;
import static org.springframework.data.relational.core.query.Query.query;

@Component
@RequiredArgsConstructor
public class TenantRepository {
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    public Mono<Tenant> insert(Tenant tenant) {
        return r2dbcEntityTemplate.insert(tenant);
    }

    public Mono<Tenant> findById(Long tenantId) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(Tenant::getId)).is(tenantId);
        return r2dbcEntityTemplate.selectOne(query(criteria), PO_CLASS);
    }

    public Mono<Long> update(Long tenantId, Update update) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(Tenant::getId)).is(tenantId);
        return r2dbcEntityTemplate.update(PO_CLASS)
                .matching(query(criteria))
                .apply(update);
    }
}