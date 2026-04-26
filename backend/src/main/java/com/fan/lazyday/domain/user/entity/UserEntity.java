package com.fan.lazyday.domain.user.entity;

import com.fan.lazyday.domain.user.po.User;
import com.fan.lazyday.domain.user.repository.UserRepository;
import com.fan.lazyday.infrastructure.context.SpringContext;
import com.fan.lazyday.infrastructure.domain.Entity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.data.util.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;


/**
 * <p>描述: [逻辑文件] </p>
 * <p>创建时间: 2024/9/12 </p>
 *
 * @author fanqibu
 * @version v1.0 <br/>
 * 2024/09/12 11:03 fan 创建
 */
@Getter
public class UserEntity extends Entity<UUID> {

    public final static Class<User> PO_CLASS = User.class;

    public final static Lazy<Context> CTX = SpringContext.getLazyBean(Context.class);

    protected UserEntity() {
    }

    @Setter(AccessLevel.PROTECTED)
    private User delegate;

    public UUID getId() {
        if (delegate != null) {
            return delegate.getId();
        }
        return null;
    }

    @Getter
    @Component
    @AllArgsConstructor
    public static class Context  {
        private final UserRepository repository;
        private final R2dbcEntityTemplate entityTemplate;
    }
}
