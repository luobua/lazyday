package com.fan.lazyday.domain.tenant.entity;

import com.fan.lazyday.domain.tenant.po.Tenant;
import com.fan.lazyday.domain.tenant.repository.TenantRepository;
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
public class TenantEntity extends Entity<Long> {

    public static final Class<Tenant> PO_CLASS = Tenant.class;

    public static final Lazy<Context> CTX = SpringContext.getLazyBean(Context.class);

    protected TenantEntity() {
    }

    @Setter(AccessLevel.PROTECTED)
    private Tenant delegate;

    public Long getId() {
        if (delegate != null) {
            return delegate.getId();
        }
        return null;
    }

    public static TenantEntity fromPo(Tenant po) {
        TenantEntity entity = new TenantEntity();
        entity.setDelegate(po);
        return entity;
    }

    public static TenantEntity create(String name, String contactEmail) {
        Tenant po = new Tenant();
        po.setName(name);
        po.setStatus("ACTIVE");
        po.setPlanType("FREE");
        po.setContactEmail(contactEmail);

        TenantEntity entity = new TenantEntity();
        entity.setDelegate(po);
        return entity;
    }

    @Getter
    @Component
    @AllArgsConstructor
    public static class Context {
        private final TenantRepository repository;
        private final R2dbcEntityTemplate entityTemplate;
    }
}