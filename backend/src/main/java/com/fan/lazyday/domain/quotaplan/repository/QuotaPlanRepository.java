package com.fan.lazyday.domain.quotaplan.repository;

import com.fan.lazyday.domain.quotaplan.po.QuotaPlan;
import com.fan.lazyday.infrastructure.helper.R2dbcHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.fan.lazyday.domain.quotaplan.entity.QuotaPlanEntity.PO_CLASS;
import static org.springframework.data.relational.core.query.Query.query;

@Component
@RequiredArgsConstructor
public class QuotaPlanRepository {
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    public Mono<QuotaPlan> insert(QuotaPlan plan) {
        return r2dbcEntityTemplate.insert(plan);
    }

    public Mono<QuotaPlan> save(QuotaPlan plan) {
        return insert(plan);
    }

    public Flux<QuotaPlan> findAll() {
        return r2dbcEntityTemplate.select(PO_CLASS).all();
    }

    public Flux<QuotaPlan> findAllActive() {
        return findByStatus("ACTIVE");
    }

    public Flux<QuotaPlan> findByStatus(String status) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(QuotaPlan::getStatus)).is(status);
        return r2dbcEntityTemplate.select(query(criteria), PO_CLASS);
    }

    public Mono<QuotaPlan> findById(Long id) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(QuotaPlan::getId)).is(id);
        return r2dbcEntityTemplate.selectOne(query(criteria), PO_CLASS);
    }

    public Mono<Long> updateById(Long id, Update update) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(QuotaPlan::getId)).is(id);
        return r2dbcEntityTemplate.update(PO_CLASS)
                .matching(query(criteria))
                .apply(update);
    }

    public Mono<Long> softDeleteById(Long id) {
        return updateById(id, Update.update("status", "DISABLED"));
    }
}
