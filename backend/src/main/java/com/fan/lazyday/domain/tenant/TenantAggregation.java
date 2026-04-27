package com.fan.lazyday.domain.tenant;

import com.fan.lazyday.domain.tenant.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
public class TenantAggregation {

    @Setter
    private TenantEntity tenantEntity;

    public Long getId() {
        if (tenantEntity != null) {
            return tenantEntity.getId();
        }
        return null;
    }

    public String getName() {
        if (tenantEntity != null) {
            return tenantEntity.getDelegate().getName();
        }
        return null;
    }
}