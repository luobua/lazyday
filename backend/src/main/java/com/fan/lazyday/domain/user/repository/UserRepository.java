package com.fan.lazyday.domain.user.repository;

import com.fan.lazyday.domain.user.po.User;
import com.fan.lazyday.infrastructure.helper.R2dbcHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

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

    public Flux<User> getAll(List<UUID> ids) {
        Criteria criteria = Criteria
                .where(R2dbcHelper.toFieldName(User::getId)).in(ids);
        Query query = query(criteria);
        return r2dbcEntityTemplate.select(query, PO_CLASS);
    }
}
