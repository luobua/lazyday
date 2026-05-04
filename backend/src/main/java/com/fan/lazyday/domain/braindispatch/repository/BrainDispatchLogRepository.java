package com.fan.lazyday.domain.braindispatch.repository;

import com.fan.lazyday.domain.braindispatch.entity.BrainDispatchLogEntity;
import com.fan.lazyday.domain.braindispatch.entity.BrainDispatchStatus;
import com.fan.lazyday.domain.braindispatch.po.BrainDispatchLogPO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;
import static org.springframework.data.relational.core.query.Update.update;

@Component
@RequiredArgsConstructor
public class BrainDispatchLogRepository {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final DatabaseClient databaseClient;

    public Mono<BrainDispatchLogPO> insert(BrainDispatchLogPO log) {
        String sql = """
                INSERT INTO t_brain_dispatch_log
                    (id, msg_id, tenant_id, type, payload, status, last_error, create_user, create_time, update_user, update_time, acked_time)
                VALUES
                    (:id, :msgId, :tenantId, :type, :payload::jsonb, :status, :lastError, :createUser, :createTime, :updateUser, :updateTime, :ackedTime)
                """;
        return databaseClient.sql(sql)
                .bind("id", log.getId())
                .bind("msgId", log.getMsgId())
                .bind("tenantId", log.getTenantId())
                .bind("type", log.getType())
                .bind("payload", log.getPayload())
                .bind("status", log.getStatus())
                .bindNull("lastError", String.class)
                .bindNull("createUser", java.util.UUID.class)
                .bind("createTime", log.getCreateTime())
                .bindNull("updateUser", java.util.UUID.class)
                .bindNull("updateTime", java.time.Instant.class)
                .bindNull("ackedTime", java.time.Instant.class)
                .fetch()
                .rowsUpdated()
                .thenReturn(log);
    }

    public Mono<BrainDispatchLogPO> findByMsgId(String msgId) {
        return r2dbcEntityTemplate.selectOne(query(where("msg_id").is(msgId)), BrainDispatchLogEntity.PO_CLASS);
    }

    public Mono<Long> updateStatus(BrainDispatchLogPO log, BrainDispatchStatus... expectedStatuses) {
        Criteria criteria = where("msg_id").is(log.getMsgId());
        if (expectedStatuses != null && expectedStatuses.length > 0) {
            List<String> statuses = new ArrayList<>();
            for (BrainDispatchStatus status : expectedStatuses) {
                statuses.add(status.value());
            }
            criteria = criteria.and("status").in(statuses);
        }
        return r2dbcEntityTemplate.update(BrainDispatchLogEntity.PO_CLASS)
                .matching(query(criteria))
                .apply(update("status", log.getStatus())
                        .set("last_error", log.getLastError())
                        .set("acked_time", log.getAckedTime())
                        .set("update_time", Instant.now()));
    }

    public Flux<BrainDispatchLogPO> findSentBefore(Instant threshold) {
        return r2dbcEntityTemplate.select(
                query(where("status").is(BrainDispatchStatus.SENT.value())
                        .and("create_time").lessThanOrEquals(threshold)),
                BrainDispatchLogEntity.PO_CLASS
        );
    }

    public Mono<Long> markTimeoutBefore(Instant threshold) {
        String sql = """
                UPDATE t_brain_dispatch_log
                SET status = 'timeout',
                    last_error = 'ack timeout',
                    update_time = NOW()
                WHERE status IN ('pending', 'sent')
                  AND create_time <= :threshold
                """;
        return databaseClient.sql(sql)
                .bind("threshold", threshold)
                .fetch()
                .rowsUpdated();
    }

    public Mono<Page<BrainDispatchLogPO>> pageLogs(Long tenantId,
                                                   List<String> statuses,
                                                   Instant from,
                                                   Instant to,
                                                   String msgId,
                                                   int page,
                                                   int size) {
        List<Criteria> criteria = buildCriteria(tenantId, statuses, from, to, msgId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "create_time"));
        Query baseQuery = Query.query(Criteria.from(criteria));
        return Mono.zip(
                        r2dbcEntityTemplate.select(baseQuery.with(pageable), BrainDispatchLogEntity.PO_CLASS).collectList(),
                        r2dbcEntityTemplate.count(baseQuery, BrainDispatchLogEntity.PO_CLASS)
                )
                .map(tuple -> new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()));
    }

    private List<Criteria> buildCriteria(Long tenantId,
                                         List<String> statuses,
                                         Instant from,
                                         Instant to,
                                         String msgId) {
        List<Criteria> criteria = new ArrayList<>();
        if (tenantId != null) {
            criteria.add(Criteria.where("tenant_id").is(tenantId));
        }
        if (statuses != null && !statuses.isEmpty()) {
            criteria.add(Criteria.where("status").in(statuses));
        }
        if (from != null) {
            criteria.add(Criteria.where("create_time").greaterThanOrEquals(from));
        }
        if (to != null) {
            criteria.add(Criteria.where("create_time").lessThanOrEquals(to));
        }
        if (msgId != null && !msgId.isBlank()) {
            criteria.add(Criteria.where("msg_id").is(msgId));
        }
        if (criteria.isEmpty()) {
            criteria.add(Criteria.empty());
        }
        return criteria;
    }

    public static BrainDispatchLogPO newPending(Long id,
                                                String msgId,
                                                Long tenantId,
                                                String type,
                                                String payload,
                                                Instant createdTime) {
        BrainDispatchLogPO log = new BrainDispatchLogPO()
                .setId(id)
                .setMsgId(msgId)
                .setTenantId(tenantId)
                .setType(type)
                .setPayload(payload)
                .setStatus(BrainDispatchStatus.PENDING.value());
        log.setCreateTime(createdTime);
        return log;
    }
}
