package com.fan.lazyday.domain.user;

import com.fan.lazyday.domain.user.entity.UserEntity;
import com.fan.lazyday.infrastructure.domain.BatchInsertAggregation;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;


/**
 * <p>描述: [类型描述] </p>
 * <p>创建时间: 2024/9/12 </p>
 *
 * @author fanqibu
 * @version v1.0 <br/>
 * 2024/09/12 15:55 fan 创建
 */
@Getter
public class UserAggregation implements BatchInsertAggregation {

    @Setter
    private UserEntity userEntity;
    @Setter(value = AccessLevel.PROTECTED)
    private Long id;


    public Long getId() {
        if (id != null) {
            return id;
        }
        if (userEntity != null) {
            return userEntity.getId();
        }

        return null;
    }

    public String getName() {
        if (userEntity != null) {
            return userEntity.getDelegate().getName();
        }
        return null;
    }
}
