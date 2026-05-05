package com.fan.lazyday.domain.braindispatch.repository;

import com.fan.lazyday.domain.braindispatch.entity.BrainDispatchLogEntity;
import com.fan.lazyday.domain.braindispatch.entity.BrainDispatchStatus;
import com.fan.lazyday.domain.braindispatch.po.BrainDispatchLogPO;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
        String sql = """
                SELECT id, msg_id, tenant_id, type, payload::text AS payload_str,
                       status, last_error, create_user, create_time,
                       update_user, update_time, acked_time
                FROM t_brain_dispatch_log
                WHERE msg_id = :msgId
                LIMIT 1
                """;
        return databaseClient.sql(sql)
                .bind("msgId", msgId)
                .map((row, meta) -> mapRow(row))
                .one();
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
        String sql = """
                SELECT id, msg_id, tenant_id, type, payload::text AS payload_str,
                       status, last_error, create_user, create_time,
                       update_user, update_time, acked_time
                FROM t_brain_dispatch_log
                WHERE status = 'sent'
                  AND create_time <= :threshold
                """;
        return databaseClient.sql(sql)
                .bind("threshold", threshold)
                .map((row, meta) -> mapRow(row))
                .all();
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
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (tenantId != null) where.append(" AND tenant_id = :tenantId");
        if (statuses != null && !statuses.isEmpty()) {
            where.append(" AND status IN (");
            for (int i = 0; i < statuses.size(); i++) {
                where.append(i == 0 ? ":s" + i : ", :s" + i);
            }
            where.append(")");
        }
        if (from != null) where.append(" AND create_time >= :from");
        if (to != null) where.append(" AND create_time <= :to");
        if (msgId != null && !msgId.isBlank()) where.append(" AND msg_id = :msgId");

        String countSql = "SELECT COUNT(*) FROM t_brain_dispatch_log" + where;
        String dataSql = "SELECT id, msg_id, tenant_id, type, payload::text AS payload_str," +
                " status, last_error, create_user, create_time," +
                " update_user, update_time, acked_time" +
                " FROM t_brain_dispatch_log" + where +
                " ORDER BY create_time DESC LIMIT :limit OFFSET :offset";

        Pageable pageable = PageRequest.of(page, size);
        int offset = page * size;

        DatabaseClient.GenericExecuteSpec countSpec = databaseClient.sql(countSql);
        DatabaseClient.GenericExecuteSpec dataSpec = databaseClient.sql(dataSql);

        if (tenantId != null) {
            countSpec = countSpec.bind("tenantId", tenantId);
            dataSpec = dataSpec.bind("tenantId", tenantId);
        }
        if (statuses != null) {
            for (int i = 0; i < statuses.size(); i++) {
                countSpec = countSpec.bind("s" + i, statuses.get(i));
                dataSpec = dataSpec.bind("s" + i, statuses.get(i));
            }
        }
        if (from != null) {
            countSpec = countSpec.bind("from", from);
            dataSpec = dataSpec.bind("from", from);
        }
        if (to != null) {
            countSpec = countSpec.bind("to", to);
            dataSpec = dataSpec.bind("to", to);
        }
        if (msgId != null && !msgId.isBlank()) {
            countSpec = countSpec.bind("msgId", msgId);
            dataSpec = dataSpec.bind("msgId", msgId);
        }
        dataSpec = dataSpec.bind("limit", size).bind("offset", offset);

        DatabaseClient.GenericExecuteSpec finalCountSpec = countSpec;
        DatabaseClient.GenericExecuteSpec finalDataSpec = dataSpec;

        return Mono.zip(
                finalDataSpec.map((row, meta) -> mapRow(row)).all().collectList(),
                finalCountSpec.map((row, meta) -> row.get(0, Long.class)).one().defaultIfEmpty(0L)
        ).map(tuple -> new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()));
    }

    private BrainDispatchLogPO mapRow(Row row) {
        BrainDispatchLogPO po = new BrainDispatchLogPO()
                .setId(row.get("id", Long.class))
                .setMsgId(row.get("msg_id", String.class))
                .setTenantId(row.get("tenant_id", Long.class))
                .setType(row.get("type", String.class))
                .setPayload(row.get("payload_str", String.class))
                .setStatus(row.get("status", String.class))
                .setLastError(row.get("last_error", String.class))
                .setAckedTime(row.get("acked_time", Instant.class));
        po.setCreateTime(row.get("create_time", Instant.class));
        po.setUpdateTime(row.get("update_time", Instant.class));
        return po;
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
