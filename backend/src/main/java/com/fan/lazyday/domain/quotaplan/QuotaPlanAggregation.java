package com.fan.lazyday.domain.quotaplan;

import com.fan.lazyday.domain.quotaplan.entity.QuotaPlanEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
public class QuotaPlanAggregation {

    @Setter
    private QuotaPlanEntity quotaPlanEntity;

    public Long getId() {
        if (quotaPlanEntity != null) {
            return quotaPlanEntity.getId();
        }
        return null;
    }
}
