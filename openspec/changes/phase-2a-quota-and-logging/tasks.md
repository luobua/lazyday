## 1. 数据层 — Flyway V3 迁移脚本

- [x] 1.1 编写 `V3__init_quota_and_log.sql`：创建 `t_quota_plan`（id, name, qps_limit, daily_limit, monthly_limit, max_app_keys, status, 审计字段）
- [x] 1.2 同脚本：创建 `t_tenant_quota`（id, tenant_id UNIQUE, plan_id, custom_qps_limit, custom_daily_limit, custom_monthly_limit, custom_max_app_keys, 审计字段 + 索引 idx_tenant_quota_tenant）
- [x] 1.3 同脚本：创建 `t_call_log` 主表（id BIGINT PK, tenant_id, app_key, path, method, status_code, latency_ms, client_ip, error_msg, request_time）`PARTITION BY RANGE (request_time)`
- [x] 1.4 同脚本：创建当月 + 下月分区表（命名 `t_call_log_YYYY_MM`）
- [x] 1.5 同脚本：在主表上创建 `idx_call_log_tenant_time(tenant_id, request_time)` + `idx_call_log_app_key(app_key)`
- [x] 1.6 同脚本：种子默认套餐（Free / Pro / Enterprise，参数对齐 design D6 的 max_app_keys=-1 表示无限）
- [x] 1.7 验证迁移：`./mvnw spring-boot:run` 能成功应用 V3，PostgreSQL 中三张表 + 两个分区存在

## 2. Backend 域模型 — QuotaPlan 域

- [x] 2.1 创建 `QuotaPlanPO`（@Table("t_quota_plan")，继承 BaseAllUserTime）
- [x] 2.2 创建 `QuotaPlanRepository`（@Component，R2dbcEntityTemplate；方法：findById / findAll / findByStatus / save / updateById）
- [x] 2.3 创建 `QuotaPlanEntity`（Lazy CTX 模式；业务方法：activate / disable / update）
- [x] 2.4 创建 `QuotaPlanAggregation`

## 3. Backend 域模型 — TenantQuota 域

- [x] 3.1 创建 `TenantQuotaPO`（@Table("t_tenant_quota")）
- [x] 3.2 创建 `TenantQuotaRepository`（方法均强制带 tenantId 参数：findByTenantId / save / updateByTenantId / deleteByTenantId）
- [x] 3.3 创建 `TenantQuotaEntity`（封装"有效配额计算"业务逻辑：effectiveQps = COALESCE(custom_qps, plan.qps)）
- [x] 3.4 创建 `TenantQuotaAggregation`

## 4. Backend 域模型 — CallLog 域

- [x] 4.1 创建 `CallLogPO`（@Table("t_call_log")，**不继承 BaseAllUserTime**——日志只有 request_time，无审计四字段）
- [x] 4.2 创建 `CallLogRepository`（方法：insert（应用层指定 id）/ findByTenantIdPaged（tenantId + timeRange + 分页）/ countByTenantIdAndTimeRange / aggregateByHour / aggregateByDay）
- [x] 4.3 不建 Entity / Aggregation（design D4 已说明）

## 5. Backend Application — Facade

- [x] 5.1 创建 `QuotaFacade` 接口（listPlans / createPlan / updatePlan / disablePlan / getEffectiveQuota(tenantId) / bindTenantPlan / overrideTenantQuota）
- [x] 5.2 创建 `QuotaFacadeImpl`（事务：bindTenantPlan 使用 TransactionalOperator；自定义覆盖修改实时落库）
- [x] 5.3 创建 `CallLogFacade` 接口（recordAsync(po) → Mono<Void> / queryPaged / aggregateByHour / aggregateByDay）
- [x] 5.4 创建 `CallLogFacadeImpl`（recordAsync 内部 .subscribe() 切到 boundedElastic，错误降级 log.warn）

## 6. Backend Infrastructure — 路由注解

- [x] 6.1 创建 `@RequestMappingInternal` 注解 → 前缀 `/internal/v1`
- [x] 6.2 在 `ContextPathConfiguration` 注册 internalContextPathV1
- [x] 6.3 在 `ServiceProperties` 添加 `internalContextPathV1` + `internalApiKey` 属性，`application.yaml` 配置默认值（环境变量覆盖）

## 7. Backend Infrastructure — Internal API 鉴权

- [x] 7.1 在 `JwtAuthWebFilter` 中添加 `/internal/**` 路径豁免逻辑
- [x] 7.2 创建 `InternalApiKeyFilter`（仅作用于 `/internal/**`，校验 `X-Internal-Api-Key` header；缺失或不匹配返回 403 INTERNAL_AUTH_FAILED）
- [x] 7.3 配置 Filter Order（在 RoleAuthorizationFilter 之前）

## 8. Backend Infrastructure — RateLimitWebFilter

- [x] 8.1 引入 Bucket4j 依赖（`bucket4j-core`，纯内存版）到 pom.xml
- [x] 8.2 创建 `RateLimitWebFilter`：路径前缀过滤（仅 `/api/{open,portal,admin}/v1/**`，排除 `/auth/**`）
- [x] 8.3 实现 tenantId 解析（优先 X-Tenant-Id header → 回退 JWT TenantContext）；无 tenant 则跳过
- [x] 8.4 实现 Bucket Caffeine 缓存（key=tenantId，maxSize=10000，expireAfterAccess=30min）
- [x] 8.5 实现令牌桶配额（容量 = effective_qps，refill = 每秒 effective_qps）
- [x] 8.6 实现日/月计数（CallLogRepository.countByTenantIdAndTimeRange + Caffeine 30s TTL 缓存）
- [x] 8.7 超限返回 HTTP 429 + 响应头 `X-RateLimit-Limit` / `X-RateLimit-Remaining` / `X-RateLimit-Reset` / `Retry-After`
- [x] 8.8 在源码 javadoc 与类注释中明确"Phase 2b Edge 上线后此 Filter 退化为回源兜底"（design D8）

## 9. Backend Infrastructure — CallLogWebFilter

- [x] 9.1 创建 `CallLogWebFilter`：相同路径前缀过滤（与 RateLimit 保持一致）
- [x] 9.2 在请求开始记录起始时间戳，在 response `doFinally` 钩子构造 CallLogPO（id 用 SnowflakeId 生成）
- [x] 9.3 调用 `callLogFacade.recordAsync(po)`（fire-and-forget）
- [x] 9.4 失败容错：`onErrorResume` log.warn + metric `lazyday.calllog.write.failed`
- [x] 9.5 配置 Filter Order（在 RateLimitWebFilter 之后）

## 10. Backend Infrastructure — PartitionScheduler

- [x] 10.1 创建 `PartitionScheduler`：`@Scheduled(cron="0 0 2 28-31 * ?")` 每月末 02:00 触发
- [x] 10.2 实现"今日是否本月最后一天"判断（避免 28-30 日空跑）
- [x] 10.3 创建下下月分区（DDL：`CREATE TABLE IF NOT EXISTS t_call_log_YYYY_MM PARTITION OF t_call_log FOR VALUES FROM ... TO ...`）
- [x] 10.4 在 LazydayApplication 启动时执行一次"确保未来 2 个月分区存在"的兜底逻辑（防止首次部署或 cron 漏跑）
- [x] 10.5 在 `LazydayApplication` 加 `@EnableScheduling`

## 11. Backend API — Portal Quota

- [x] 11.1 创建 `PortalQuotaApi` 接口（`GET /quota`）
- [x] 11.2 创建 `PortalQuotaHandler`（@RequestMappingPortalV1）
- [x] 11.3 实现 `GET /quota`：返回当前租户的 effective quota + 当日用量 + 当月用量 + 套餐名称（EffectiveQuotaResponse）

## 12. Backend API — Portal Logs

- [x] 12.1 创建 `PortalLogApi` 接口（`GET /logs` 分页 / `GET /logs/stats` 聚合）
- [x] 12.2 创建 `PortalLogHandler`
- [x] 12.3 实现 `GET /logs`（参数：page, size, startTime, endTime, statusCode 可选；强制带 startTime+endTime）
- [x] 12.4 实现 `GET /logs/stats`（参数：startTime, endTime, granularity=hour|day）

## 13. Backend API — Admin Plan

- [x] 13.1 创建 `AdminPlanApi` 接口（GET 列表 / POST 创建 / PUT 更新 / DELETE 禁用）
- [x] 13.2 创建 `AdminPlanHandler`（@RequestMappingAdminV1）
- [x] 13.3 实现 GET `/plans`（返回所有套餐含 status）
- [x] 13.4 实现 POST `/plans`（CreatePlanRequest 校验：qps>0 / daily>0 / monthly>0）
- [x] 13.5 实现 PUT `/plans/{id}`（UpdatePlanRequest）
- [x] 13.6 实现 DELETE `/plans/{id}`（软禁用 status=DISABLED；若有租户绑定返回 409 PLAN_IN_USE）

## 14. Backend API — Admin TenantQuota

- [x] 14.1 创建 `AdminTenantQuotaApi` 接口（PUT `/tenants/{tenantId}/quota`）
- [x] 14.2 创建 `AdminTenantQuotaHandler`
- [x] 14.3 实现 PUT `/tenants/{tenantId}/quota`（OverrideQuotaRequest：planId 必填 + 4 个 custom 字段可选 null）

## 15. Backend API — Internal Quota

- [x] 15.1 创建 `InternalQuotaApi` 接口（GET `/internal/v1/quota/effective?tenantId=`）
- [x] 15.2 创建 `InternalQuotaHandler`（@RequestMappingInternal）
- [x] 15.3 实现：返回 EffectiveQuotaResponse（不含用量统计，只返有效限额）

## 16. Backend — DTOs

- [x] 16.1 `CreatePlanRequest` / `UpdatePlanRequest` / `OverrideQuotaRequest`
- [x] 16.2 `QuotaPlanResponse` / `EffectiveQuotaResponse` / `QuotaUsageResponse`
- [x] 16.3 `CallLogResponse` / `CallLogStatsResponse` / `PageResponse<T>`

## 17. Backend — 错误码

- [x] 17.1 在 ErrorCode 枚举（或常量类）中新增：`RATE_LIMIT_EXCEEDED` (429) / `QUOTA_DAILY_EXCEEDED` (429) / `QUOTA_MONTHLY_EXCEEDED` (429) / `PLAN_IN_USE` (409) / `PLAN_NOT_FOUND` (404) / `INTERNAL_AUTH_FAILED` (403) / `PARTITION_MISSING` (500)
- [x] 17.2 在 GlobalExceptionHandler 中映射上述错误

## 18. Backend — 单元测试

- [x] 18.1 `QuotaPlanRepositoryTest`（CRUD）
- [x] 18.2 `TenantQuotaRepositoryTest`（含 tenantId 强制隔离）
- [x] 18.3 `CallLogRepositoryTest`（写入 + 分页 + 聚合）
- [x] 18.4 `QuotaFacadeImplTest`（有效配额计算覆盖逻辑）
- [x] 18.5 `RateLimitWebFilterTest`（StepVerifier 验证 429 + 响应头）
- [x] 18.6 `CallLogWebFilterTest`（验证 fire-and-forget 不阻塞响应 + 失败不抛错）
- [x] 18.7 `PartitionSchedulerTest`（mock 当前时间，验证下下月分区 SQL 生成）

## 19. 端到端联调 + 验收

- [x] 19.1 启动 Backend，应用 V3 迁移成功，三张表存在
- [x] 19.2 默认 Free 套餐被自动绑定到现有所有租户（迁移脚本中处理或应用层兜底）
- [x] 19.3 触发 QPS 超限：以 Phase 1 测试租户调用 `/api/portal/v1/quota` 超过 5 QPS，返回 429 + `Retry-After` 头
- [x] 19.4 触发日配额超限：mock t_call_log 中插入 1000 条当日记录，第 1001 次返回 429 QUOTA_DAILY_EXCEEDED
- [x] 19.5 调用日志可查询：发起一次 `/api/portal/v1/quota` 调用后，5 秒内通过 `/api/portal/v1/logs` 可看到该条记录
- [x] 19.6 横向隔离：Tenant A 调用 `/api/portal/v1/logs` 返回的全部记录 tenant_id = A
- [x] 19.7 Admin 创建 Pro 套餐 → 给 Tenant A 绑定 Pro → Tenant A 的 `/quota` 立即返回 Pro 的限额
- [x] 19.8 Admin 给 Tenant A 设置 custom_qps=200 → Tenant A 的 effective_qps=200（覆盖 Pro 的 50）
- [x] 19.9 Internal API：用错误的 X-Internal-Api-Key 访问 `/internal/v1/quota/effective` 返回 403
- [x] 19.10 Partition：手动触发 PartitionScheduler，下下月分区被创建
- [x] 19.11 套餐被绑定时尝试 DELETE 返回 409 PLAN_IN_USE
