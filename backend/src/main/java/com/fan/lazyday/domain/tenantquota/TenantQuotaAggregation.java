package com.fan.lazyday.domain.tenantquota;

import com.fan.lazyday.domain.tenantquota.entity.TenantQuotaEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
public class TenantQuotaAggregation {

    @Setter
    private TenantQuotaEntity tenantQuotaEntity;

    public Long getId() {
        if (tenantQuotaEntity != null) {
            return tenantQuotaEntity.getId();
        }
        return null;
    }
}
