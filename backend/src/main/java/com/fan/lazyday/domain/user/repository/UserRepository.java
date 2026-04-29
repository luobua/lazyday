package com.fan.lazyday.domain.user.repository;

import com.fan.lazyday.domain.user.po.User;
import com.fan.lazyday.infrastructure.helper.R2dbcHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static com.fan.lazyday.domain.user.entity.UserEntity.PO_CLASS;
import static org.springframework.data.relational.core.query.Query.query;

/**
 * <p>描述: [逻辑文件] </p>
 * <p>创建时间: 2024/9/12 </p>
 *
 * @author fanqibu
 * @version v1.0 <br/>
 * 2024/09/12 11:03 fan 创建
 */
@Component
@RequiredArgsConstructor
public class UserRepository {
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    public Flux<User> getAll(List<Long> ids) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(User::getId)).in(ids);
        Query query = query(criteria);
        return r2dbcEntityTemplate.select(query, PO_CLASS);
    }

    public Mono<User> insert(User user) {
        return r2dbcEntityTemplate.insert(user);
    }

    public Mono<User> findByUsername(String username) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(User::getUsername)).is(username);
        return r2dbcEntityTemplate.selectOne(query(criteria), PO_CLASS);
    }

    public Mono<User> findByEmail(String email) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(User::getEmail)).is(email);
        return r2dbcEntityTemplate.selectOne(query(criteria), PO_CLASS);
    }

    public Mono<User> findById(Long id) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(User::getId)).is(id);
        return r2dbcEntityTemplate.selectOne(query(criteria), PO_CLASS);
    }

    public Flux<String> findTenantAdminEmailsByTenantId(Long tenantId) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(User::getTenantId)).is(tenantId)
                .and(R2dbcHelper.toFieldName(User::getRole)).is("TENANT_ADMIN")
                .and(R2dbcHelper.toFieldName(User::getStatus)).is("ACTIVE");
        return r2dbcEntityTemplate.select(query(criteria), PO_CLASS)
                .map(User::getEmail)
                .filter(email -> email != null && !email.isBlank());
    }

    public Mono<Long> updateById(Long id, Update update) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(User::getId)).is(id);
        return r2dbcEntityTemplate.update(PO_CLASS)
                .matching(query(criteria))
                .apply(update);
    }
}
