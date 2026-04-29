package com.fan.lazyday.domain.tenantquota.entity;

import com.fan.lazyday.domain.quotaplan.po.QuotaPlan;
import com.fan.lazyday.domain.tenantquota.po.TenantQuota;
import com.fan.lazyday.domain.tenantquota.repository.TenantQuotaRepository;
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
public class TenantQuotaEntity extends Entity<Long> {

    public static final Class<TenantQuota> PO_CLASS = TenantQuota.class;

    public static final Lazy<Context> CTX = SpringContext.getLazyBean(Context.class);

    protected TenantQuotaEntity() {
    }

    @Setter(AccessLevel.PROTECTED)
    private TenantQuota delegate;

    public Long getId() {
        if (delegate != null) {
            return delegate.getId();
        }
        return null;
    }

    public static TenantQuotaEntity fromPo(TenantQuota po) {
        TenantQuotaEntity entity = new TenantQuotaEntity();
        entity.setDelegate(po);
        return entity;
    }

    /**
     * Resolve effective quota: custom value takes priority over plan default.
     */
    public int getEffectiveQpsLimit(QuotaPlan plan) {
        return delegate.getCustomQpsLimit() != null ? delegate.getCustomQpsLimit() : plan.getQpsLimit();
    }

    public long getEffectiveDailyLimit(QuotaPlan plan) {
        return delegate.getCustomDailyLimit() != null ? delegate.getCustomDailyLimit() : plan.getDailyLimit();
    }

    public long getEffectiveMonthlyLimit(QuotaPlan plan) {
        return delegate.getCustomMonthlyLimit() != null ? delegate.getCustomMonthlyLimit() : plan.getMonthlyLimit();
    }

    public int getEffectiveMaxAppKeys(QuotaPlan plan) {
        return delegate.getCustomMaxAppKeys() != null ? delegate.getCustomMaxAppKeys() : plan.getMaxAppKeys();
    }

    @Getter
    @Component
    @AllArgsConstructor
    public static class Context {
        private final TenantQuotaRepository repository;
        private final R2dbcEntityTemplate entityTemplate;
    }
}
