## Context

Phase 1 已落地 DDD 分层 + JWT 鉴权 + Tenant/AppKey/User 域。Open API（HMAC 验签）原计划与 Edge 同期交付，但本提案不强依赖 Edge，因此 Open API 的 HMAC 验签**仍延后到 Phase 2b**，Phase 2a 仅基于 Phase 1 的 JWT TenantContext 解析当前 tenant。

Edge 网关项目尚未启动。本期不在 Edge 中实现限流/日志，转而在 Backend 进程中实现 WebFilter 版限流/日志，保证 Phase 2a 自身闭环可用，并为 Phase 2b 的 Edge 上线提供"回源兜底"。

工作目录里有 32 个未提交文件作为蓝图参考（V3 SQL、quotaplan/tenantquota/calllog 域、RateLimit/CallLog WebFilter、PartitionScheduler、5 个 Handler 与对应 API/Request/Response）。本提案以它们为视觉参考，但**最终代码以 tasks.md 为准**——可能复用、可能重写。

## Goals / Non-Goals

**Goals**
- 套餐与租户配额完整数据模型与管理 API
- 调用日志按月分区写入 + 分页查询 + 聚合统计
- Backend 自身的进程级 QPS/日/月限流（429 + RFC 7807 兼容头）
- 调用日志异步采集，不影响响应延迟
- 月度分区自动滚动（不依赖 pg_cron 扩展，纯应用层）
- Internal API 供 Phase 2b 的 Edge 调用查询有效配额

**Non-Goals**
- Edge 网关项目搭建（Phase 2b）
- Redis 限流（Phase 2b，与 Edge 一起）
- WebSocket 双向通道（Phase 2b）
- Resilience4j 熔断（Phase 2b）
- Frontend 日志查询页 / 统计看板 / CSV 导出（Phase 2b）
- Open API HMAC 验签（Phase 2b 与 Edge 一起）
- 调用日志 30 天预聚合表（Phase 5 V5）
- 邮件配额告警（Phase 3）

## Decisions

### D1：迁移版本号 — V3（不是 plan 中假设的 V2）

**背景**：Phase 1 已用掉 V2（`V2__seed_platform_admin.sql`），原 `iteration-plan.md` 中"V2__init_quota_and_log.sql"的命名与现实冲突。

**选择**：本期迁移命名为 `V3__init_quota_and_log.sql`，单脚本同时建配额表 + 日志分区表 + 默认套餐种子。

**理由**：单脚本更易回滚、更易在 Phase 2b 之前完成 schema 冻结。

### D2：调用日志按月分区 — 应用层维护，不依赖 pg_cron

**选择**：
- `t_call_log` 用 `PARTITION BY RANGE (request_time)` 按月分区
- V3 脚本初始化时创建当月 + 下月分区
- `PartitionScheduler` 每月最后一天 02:00（CRON：`0 0 2 28-31 * ?` + 应用层校验"最后一天"）创建下下月分区
- 命名规则：`t_call_log_YYYY_MM`
- 失败兜底：应用层若发现写入分区不存在，抛 `PARTITION_MISSING` 错误码并触发紧急建分区任务

**替代方案**：
- pg_cron 扩展：阿里云 RDS 部分版本不支持，环境耦合
- 不分区：日志表预期月增千万级，单表性能与运维不可接受

**理由**：应用层调度对底层 PostgreSQL 版本无要求；Spring Scheduling 本就在用，零新增依赖。

### D3：调用日志 ID — 雪花算法，应用层生成

**选择**：复用 Phase 1 已有的 SnowflakeId 工具，CallLogPO 写入前由应用层注入 id（不用数据库 IDENTITY）。

**理由**：
- 分区表的 IDENTITY 序列在分区切换时存在边界争用风险
- 高并发写入避免数据库自增热点
- 与 `iteration-plan.md` §7 "调用日志表雪花算法"决策一致

### D4：CallLog 不建 Aggregation

**选择**：CallLog 仅有 PO + Repository + Facade，不建 Entity / Aggregation。

**理由**：调用日志是不可变的"事实流"——只写不改、查询/聚合即可，没有业务行为，建 Aggregation 是过度设计。Phase 1 的 Tenant/AppKey 有状态机（启用/禁用/轮换）才需要 Entity。

### D5：限流实现 — Bucket4j 纯内存版（不引入 Redis）

**选择**：单实例 Bucket4j 令牌桶，按 `tenantId` 缓存 Bucket 实例（Caffeine LRU + 30 分钟空闲过期）。

**替代方案**：
- Redis + Bucket4j 分布式版：Phase 2a 仅 Backend 单实例（或少量实例），暂不需要分布式限流；Phase 2b Edge 上线后限流主战场迁到 Edge 的 Redis Bucket4j。
- 自实现令牌桶：重复造轮子，不必要

**理由**：Backend 多实例下"宽松限流"可接受（每实例独立桶，最坏放过 N 倍流量给 Edge 兜底）；Phase 2b Edge 是主限流。Bucket4j 库轻量、API 友好。

### D6：日/月配额计数 — 数据库 SELECT COUNT + 内存短期缓存

**选择**：
- 日/月配额检查：`SELECT COUNT(*) FROM t_call_log WHERE tenant_id = ? AND request_time >= ?`
- 结果缓存到 Caffeine（key=`{tenantId}:{day|month}`，TTL = 30 秒）
- 超限直接 429，不再扣令牌桶

**替代方案**：
- Redis Counter（INCR + EXPIRE）：Phase 2b Edge 实施
- 不做日/月配额：违反 Phase 2 验收标准

**理由**：30 秒缓存意味着最差延迟 30 秒生效（业务可接受）；DB COUNT 在 `idx_call_log_tenant_time` 索引下当日数据扫描在毫秒级；避免引入 Redis。Phase 2b 上 Redis 时再切换。

### D7：CallLogWebFilter — 异步 fire-and-forget

**选择**：
- 在响应 `doFinally` 钩子中构造 CallLogPO，调用 `callLogFacade.recordAsync(po)` 返回 `Mono<Void>` 但**不订阅在主请求链上**——通过 `.subscribe()` 在 boundedElastic 上独立执行
- 写入失败：`Mono.onErrorResume(err -> { log.warn(...); return Mono.empty(); })`，不影响响应
- 不引入消息队列（Phase 2b Edge 用 WebSocket 批量推送时再考虑）

**风险与缓解**：
- 进程崩溃可能丢失未刷盘的日志（< 1 秒级别）— 业务可接受，调用方有自己的请求记录
- 高并发下 boundedElastic 线程池可能堆积 — Phase 2a 加 `Sinks.Many.multicast().onBackpressureBuffer()` 做缓冲，超限丢弃并打警告日志

### D8：RateLimitWebFilter 与未来 Edge GlobalFilter 的关系

**定位（写入代码注释和 design）**：
- Phase 2a：RateLimitWebFilter 是**主限流入口**
- Phase 2b：Edge GlobalFilter 上线后，RateLimitWebFilter 退化为**回源兜底**——防止外部直接绕过 Edge 直连 Backend
- 限流策略一致（同一份 t_tenant_quota 数据），但实例不同（各自有桶）；不去重计数

**部署边界**：Backend 容器在 K8s 中应仅暴露给 Edge Service（不对外网开放）。Phase 2b 文档化此约束。

### D9：Internal API 路径与鉴权

**选择**：
- 新增 `@RequestMappingInternal` 注解 → `/internal/**` 前缀
- Internal 路径在 JwtAuthWebFilter 中标记为豁免，但通过 `internal-api-key` header 校验（在 ServiceProperties 中配置共享密钥）
- Phase 2b Edge 启动时配置同一密钥

**理由**：Internal API 不走 JWT（Edge 是机器调用，无用户上下文）；K8s 内网信任不足够（多团队共享集群），加 header 密钥做最小防御。

### D10：路径前缀策略 — RateLimit/CallLog WebFilter 仅作用于受控前缀

**选择**：
- 限流和采日志只作用于 `/api/open/v1/**` + `/api/portal/v1/**` + `/api/admin/v1/**`
- 排除 `/internal/**`（机器调用）+ `/actuator/**`（健康检查）+ `/api/portal/v1/auth/**`（登录注册不限流）+ `/api/admin/v1/auth/**`

**理由**：登录注册被限会卡住合法用户；健康检查需要稳定通过。

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Backend 多实例下令牌桶不一致，限流可能放过 N 倍流量 | Phase 2a 接受（实例数有限）；Phase 2b Edge 主限流弥补 |
| 日/月配额 30 秒缓存延迟 → 极端突发可超额 | 业务可接受；Phase 2b 切 Redis Counter 后归零 |
| 雪花 ID 在多实例下需机器位分配 | 复用 Phase 1 已有 SnowflakeId 配置（机器位来自环境变量） |
| PartitionScheduler 任务漏跑导致下月分区缺失 | 应用启动时执行一次"确保未来 2 个月分区存在"的兜底逻辑 |
| t_call_log 分区表迁移过程中查询历史数据需跨分区 | PostgreSQL 自动并行扫描分区；查询 API 强制 time_range 参数（非闭环） |
| CallLogWebFilter 响应后写入失败丢日志 | log.warn 记录并打 metric；可接受 < 0.1% 丢失率 |
| Internal API key 泄露 | 通过 K8s Secret 注入；启用日志脱敏；Phase 5 引入 mTLS 替代 |
| Bucket4j 桶 Caffeine 缓存内存膨胀 | 限制最多 10000 个 tenant 桶 + 30 分钟空闲过期 |

## Migration Plan

无现有数据迁移。新建表 + 新建 API，向后兼容。

Phase 1 已部署的 Backend 实例升级到 Phase 2a：
1. 应用 V3 迁移
2. 新版本 Backend 启动后自动启用 RateLimitWebFilter + CallLogWebFilter
3. 现有 Portal/Admin API 调用从此刻开始记录到 `t_call_log`（非历史回填）

## Open Questions

- [ ] Internal API key 怎么轮换？Phase 2b 设计 Edge 配置热更新时一并解决
- [ ] 日志查询返回字段是否需要脱敏 client_ip（GDPR）？默认全量返回，Phase 5 视监管要求加脱敏开关
- [ ] 套餐删除是否级联？当前设计：仅 status=DISABLED 软禁用，不允许删除已被租户绑定的套餐（返回 409）