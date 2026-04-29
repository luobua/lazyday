package com.fan.lazyday.domain.webhookconfig.repository;

import com.fan.lazyday.domain.webhookconfig.po.WebhookConfigPO;
import com.fan.lazyday.infrastructure.helper.R2dbcHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.fan.lazyday.domain.webhookconfig.entity.WebhookConfigEntity.PO_CLASS;
import static org.springframework.data.relational.core.query.Query.query;

@Component
@RequiredArgsConstructor
public class WebhookConfigRepository {
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    public Mono<WebhookConfigPO> insert(WebhookConfigPO webhookConfig) {
        return r2dbcEntityTemplate.insert(webhookConfig);
    }

    public Flux<WebhookConfigPO> findByTenantId(Long tenantId) {
        Criteria criteria = Criteria.where(R2dbcHelper.toFieldName(WebhookConfigPO::getTenantId)).is(tenantId);
        return r2dbcEntityTemplate.select(query(criteria), PO_CLASS);
    }

    public Flux<WebhookConfigPO> findActiveByTenantIdAndEventType(Long tenantId, String eventType) {
        Criteria criteria = Criteria.where(R2dbcHelper.toFieldName(WebhookConfigPO::getTenantId)).is(tenantId)
                .and(R2dbcHelper.toFieldName(WebhookConfigPO::getStatus)).is("ACTIVE")
                .and(R2dbcHelper.toFieldName(WebhookConfigPO::getEventTypes)).like("%" + eventType + "%");
        return r2dbcEntityTemplate.select(query(criteria), PO_CLASS);
    }

    public Mono<WebhookConfigPO> findByIdAndTenantId(Long id, Long tenantId) {
        Criteria criteria = Criteria.where(R2dbcHelper.toFieldName(WebhookConfigPO::getId)).is(id)
                .and(R2dbcHelper.toFieldName(WebhookConfigPO::getTenantId)).is(tenantId);
        return r2dbcEntityTemplate.selectOne(query(criteria), PO_CLASS);
    }

    public Mono<Long> updateByIdAndTenantId(Long id, Long tenantId, Update update) {
        Criteria criteria = Criteria.where(R2dbcHelper.toFieldName(WebhookConfigPO::getId)).is(id)
                .and(R2dbcHelper.toFieldName(WebhookConfigPO::getTenantId)).is(tenantId);
        return r2dbcEntityTemplate.update(PO_CLASS)
                .matching(query(criteria))
                .apply(update);
    }
}
