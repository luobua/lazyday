package com.fan.lazyday.domain.appkey.repository;

import com.fan.lazyday.domain.appkey.po.AppKey;
import com.fan.lazyday.infrastructure.helper.R2dbcHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.fan.lazyday.domain.appkey.entity.AppKeyEntity.PO_CLASS;
import static org.springframework.data.relational.core.query.Query.query;

@Component
@RequiredArgsConstructor
public class AppKeyRepository {
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    public Mono<AppKey> insert(AppKey appKey) {
        return r2dbcEntityTemplate.insert(appKey);
    }

    public Flux<AppKey> findByTenantId(Long tenantId) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(AppKey::getTenantId)).is(tenantId);
        return r2dbcEntityTemplate.select(query(criteria), PO_CLASS);
    }

    public Mono<AppKey> findByIdAndTenantId(Long id, Long tenantId) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(AppKey::getId)).is(id)
                .and(R2dbcHelper.toFieldName(AppKey::getTenantId)).is(tenantId);
        return r2dbcEntityTemplate.selectOne(query(criteria), PO_CLASS);
    }

    public Mono<AppKey> findByAppKey(String appKey) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(AppKey::getAppKey)).is(appKey);
        return r2dbcEntityTemplate.selectOne(query(criteria), PO_CLASS);
    }

    public Mono<Long> updateByIdAndTenantId(Long id, Long tenantId, Update update) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(AppKey::getId)).is(id)
                .and(R2dbcHelper.toFieldName(AppKey::getTenantId)).is(tenantId);
        return r2dbcEntityTemplate.update(PO_CLASS)
                .matching(query(criteria))
                .apply(update);
    }

    public Mono<Long> deleteByIdAndTenantId(Long id, Long tenantId) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(AppKey::getId)).is(id)
                .and(R2dbcHelper.toFieldName(AppKey::getTenantId)).is(tenantId);
        return r2dbcEntityTemplate.delete(query(criteria), PO_CLASS);
    }
}