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
import org.springframework.data.util.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;



/**
 * <p>描述: [逻辑文件] </p>
 * <p>创建时间: 2024/9/12 </p>
 *
 * @author fanqibu
 * @version v1.0 <br/>
 * 2024/09/12 11:03 fan 创建
 */
@Getter
public class UserEntity extends Entity<Long> {

    public final static Class<User> PO_CLASS = User.class;

    public final static Lazy<Context> CTX = SpringContext.getLazyBean(Context.class);

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    protected UserEntity() {
    }

    @Setter(AccessLevel.PROTECTED)
    private User delegate;

    public Long getId() {
        if (delegate != null) {
            return delegate.getId();
        }
        return null;
    }

    public static UserEntity fromPo(User po) {
        UserEntity entity = new UserEntity();
        entity.setDelegate(po);
        return entity;
    }

    public static Mono<String> hashPassword(String rawPassword) {
        return Mono.fromCallable(() -> PASSWORD_ENCODER.encode(rawPassword))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Boolean> verifyPassword(String rawPassword) {
        return Mono.fromCallable(() -> PASSWORD_ENCODER.matches(rawPassword, delegate.getPasswordHash()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Getter
    @Component
    @AllArgsConstructor
    public static class Context {
        private final UserRepository repository;
        private final R2dbcEntityTemplate entityTemplate;
    }
}
