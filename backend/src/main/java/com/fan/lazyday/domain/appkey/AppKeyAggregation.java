package com.fan.lazyday.domain.appkey;

import com.fan.lazyday.domain.appkey.entity.AppKeyEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
public class AppKeyAggregation {

    @Setter
    private AppKeyEntity appKeyEntity;

    public Long getId() {
        if (appKeyEntity != null) {
            return appKeyEntity.getId();
        }
        return null;
    }
}