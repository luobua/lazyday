package com.fan.lazyday.infrastructure.domain;

import com.fan.lazyday.infrastructure.helper.R2dbcHelper;
import org.springframework.data.domain.Example;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

import java.util.List;

public interface BatchInsertAggregation {
    /**
     *查询数量
     *
     * @param repository 仓库
     * @param po         持久对象
     * @param tClass     类型
     * @param <T>        类型
     * @return Long
     */
    default <T> Mono<Long> count(R2dbcRepository<T, Long> repository, T po, Class<T> tClass) {
        Example<T> example = Example.of(po);
        return repository.count(example);
    }

    /**
     * 批量插入
     *
     * @param entities 实体
     * @param tClass   类型
     * @param <T>      类型
     * @return Integer
     */
    default <T> Mono<Long> batchInsert(List<T> entities, Class<T> tClass) {
        return R2dbcHelper.batchInsert(entities, tClass);
    }
}
