package com.fan.lazyday.domain.quotaplan.entity;

import com.fan.lazyday.domain.quotaplan.po.QuotaPlan;
import com.fan.lazyday.domain.quotaplan.repository.QuotaPlanRepository;
import com.fan.lazyday.infrastructure.context.SpringContext;
import com.fan.lazyday.infrastructure.domain.Entity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.util.Lazy;
import org.springframework.stereotype.Component;

@Getter
public class QuotaPlanEntity extends Entity<Long> {

    public static final Class<QuotaPlan> PO_CLASS = QuotaPlan.class;

    public static final Lazy<Context> CTX = SpringContext.getLazyBean(Context.class);

    protected QuotaPlanEntity() {
    }

    @Setter(AccessLevel.PROTECTED)
    private QuotaPlan delegate;

    public Long getId() {
        if (delegate != null) {
            return delegate.getId();
        }
        return null;
    }

    public static QuotaPlanEntity fromPo(QuotaPlan po) {
        QuotaPlanEntity entity = new QuotaPlanEntity();
        entity.setDelegate(po);
        return entity;
    }

    public void activate() {
        delegate.setStatus("ACTIVE");
    }

    public void disable() {
        delegate.setStatus("DISABLED");
    }

    public void update(String name, Integer qpsLimit, Long dailyLimit, Long monthlyLimit, Integer maxAppKeys) {
        if (name != null) {
            delegate.setName(name);
        }
        if (qpsLimit != null) {
            delegate.setQpsLimit(qpsLimit);
        }
        if (dailyLimit != null) {
            delegate.setDailyLimit(dailyLimit);
        }
        if (monthlyLimit != null) {
            delegate.setMonthlyLimit(monthlyLimit);
        }
        if (maxAppKeys != null) {
            delegate.setMaxAppKeys(maxAppKeys);
        }
    }

    @Getter
    @Component
    @AllArgsConstructor
    public static class Context {
        private final QuotaPlanRepository repository;
        private final R2dbcEntityTemplate entityTemplate;
    }
}
