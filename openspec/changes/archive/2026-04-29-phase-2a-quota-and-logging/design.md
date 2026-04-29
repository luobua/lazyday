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

### D6：日/月配额计数 — LongAdder 同步增量 + DB COUNT 暖启动

**选择（修订版）**：
- 进程内每 `(tenantId, dayKey)` 与 `(tenantId, monthKey)` 维护 `LongAdder` 计数器，存于 Caffeine 缓存（maxSize=20000，expireAfterWrite=次日凌晨 / 次月 1 日凌晨）
- 限流器**通过后立即** `adder.increment()`，下一次请求看到准确的累计值（同进程内零延迟）
- 缓存未命中时（首次访问 / 进程重启 / 旧 key 被淘汰）：执行一次 `SELECT COUNT(*) FROM t_call_log WHERE tenant_id = ? AND request_time >= ?` 暖启动 adder，结果加到 adder 而非缓存
- 暖启动 SQL 的预期耗时（带 `idx_call_log_tenant_time` 索引）：典型 Pro 套餐租户当日 50k 行 < 50ms；Enterprise 套餐当月 5M 行 < 200ms。**不再宣称"毫秒级"**；高负载下视为冷启动一次性成本。
- 超限直接 429，不再扣令牌桶

**替代方案**：
- 30 秒缓存版（之前的方案）：突发 N 个并发请求都看到陈旧 COUNT，可能放过 `daily_limit + N` 次。被否决。
- Redis INCR + EXPIRE：跨实例精确，但引入 Redis 依赖；Phase 2b 与 Edge 一起做
- 完全不做日/月配额：违反验收标准

**理由**：LongAdder 在同进程内消除了"30 秒陈旧窗口 + 并发暴冲"的双重问题。但跨实例仍是各自独立的 adder——见下文"Phase 2a 的精确性边界"。

### D6.1：Phase 2a 限流的精确性边界（明确告知）

**单实例**：QPS / 日 / 月限额都是精确的（令牌桶 + LongAdder 都是同进程同步）。

**多实例**（N 个 Backend 副本）：
- QPS：每副本独立令牌桶 → 实际放行 ≤ N × `qps_limit`
- 日/月：每副本独立 adder + 各自的 DB 暖启动 → 实际放行 ≤ N × `daily_limit`（极端情况下）
- 这是 Phase 2a 接受的精度损失。**Backend 多副本部署 ≠ 精确限流**。

**Phase 2b 后**：Edge GlobalFilter 用 Redis 共享计数做主限流（精确），Backend 退化为兜底（继续保留宽松版本，防直连绕过）。

### D7：CallLogWebFilter — 异步 fire-and-forget

**选择**：
- 在响应 `doFinally` 钩子中构造 CallLogPO，调用 `callLogFacade.recordAsync(po)` 返回 `Mono<Void>` 但**不订阅在主请求链上**——通过 `.subscribe()` 在 boundedElastic 上独立执行
- 写入失败：`Mono.onErrorResume(err -> { log.warn(...); return Mono.empty(); })`，不影响响应
- 不引入消息队列（Phase 2b Edge 用 WebSocket 批量推送时再考虑）

**实现细节**：
- `CallLogFacadeImpl` 内部维护一个 `Sinks.Many<CallLog>.multicast().onBackpressureBuffer(callLogBufferSize)` 队列（默认 10000）
- `recordAsync(po)` 实现为 `sink.tryEmitNext(po)`；返回 `FAIL_OVERFLOW` 时丢弃 + `lazyday.calllog.write.shed` 计数器 + WARN 日志
- 启动时以 `boundedElastic` 订阅 sink 并写库；写库失败 → `lazyday.calllog.write.failed` 计数器 + WARN，不中断订阅
- Filter 端永远不会阻塞响应链：`tryEmitNext` 是非阻塞的

**风险与缓解**：
- 进程崩溃可能丢失未刷盘的日志（< 1 秒级别）— 业务可接受，调用方有自己的请求记录
- 高并发下 buffer 满会丢日志 — 通过 metric `lazyday.calllog.write.shed` 监控丢弃率，超阈值触发告警；可配置增大 buffer 或在 Phase 2b WebSocket 上线后切换为推送 Backend 集中写

### D8：RateLimitWebFilter 与未来 Edge GlobalFilter 的关系

**定位（写入代码注释和 design）**：
- Phase 2a：RateLimitWebFilter 是**主限流入口**
- Phase 2b：Edge GlobalFilter 上线后，RateLimitWebFilter 退化为**回源兜底**——防止外部直接绕过 Edge 直连 Backend
- 限流策略一致（同一份 t_tenant_quota 数据），但实例不同（各自有桶）；不去重计数

**部署边界**：Backend 容器在 K8s 中应仅暴露给 Edge Service（不对外网开放）。Phase 2b 文档化此约束。

### D9：Internal API 路径与鉴权（增强版）

**选择**：
- 新增 `@RequestMappingInternal` 注解 → `/internal/v1/**` 前缀
- Internal 路径在 `JwtAuthWebFilter` 中标记为豁免
- 新增 `InternalApiKeyFilter` 校验 `X-Internal-Api-Key` header 与 `ServiceProperties.internalApiKey` 是否相等
- **启动时强制校验** `internalApiKey` 非空且长度 ≥ 32 字符；为空或过短直接抛 `BeanInitializationException`，应用拒绝启动（fail-closed）
- 对 `/internal/v1/**` 应用全局速率限流（单进程 100 QPS Bucket4j 桶；非 per-tenant），防止 key 泄露后被刷
- 每次 `/internal/v1/**` 调用 INFO 级别审计日志：`{ts, caller_ip, path, tenantId(若有), result}` 到独立 logger `lazyday.internal.audit`
- Phase 2b Edge 启动时通过 K8s Secret 注入同一密钥；密钥轮换通过双 key（current + previous）支持热切换 — 列入 Open Questions

**理由**：单凭共享 header key 无法防泄露后的滥用，加上限流 + 审计 + 启动期强校验做纵深防御；Phase 5 mTLS 替换 header key 是最终目标，但 Phase 2a/2b 的窗口期内 fail-closed + 审计 + 限流可以接受。

### D11：CallLog `app_key` 字段在 JWT 流量下的取值 — 哨兵值

**背景**：`t_call_log.app_key VARCHAR(64) NOT NULL` 是为 Phase 2b Open API HMAC 流量设计的（每条记录都关联一个 AppKey）。但 Phase 2a 主要流量是 Portal/Admin JWT 流量，根本没有 AppKey。

**选择**：
- Open API HMAC 流量（Phase 2b 才会真正出现，但 2a 已为其留好规范）：`app_key = X-App-Key header value` (`ak_xxx` 形式)
- Portal JWT 流量：`app_key = __PORTAL__`
- Admin JWT 流量：`app_key = __ADMIN__`
- Internal API：被排除在 CallLogWebFilter 之外（D10），不入库

**替代方案**：
- `app_key` 改为 NULL — 破坏 idx_call_log_app_key 的查询语义；NULL 不能被等值过滤器命中
- 用空字符串 `""` — 不可读，单一空桶变成索引热点且无业务含义

**理由**：
- 哨兵值可读（看一眼就知道是 Portal 还是 Admin 流量），可被索引精确过滤
- 与 Phase 1 AppKey 强制 `ak_`/`sk_` 前缀的约束互不冲突，不会误撞真实密钥
- Phase 2b Open API 上线时无需改 schema；CallLogWebFilter 内仅需把 X-App-Key 取代当前的哨兵默认即可

### D10：路径前缀策略 — RateLimit/CallLog WebFilter 仅作用于受控前缀

**选择**：
- 限流和采日志只作用于 `/api/open/v1/**` + `/api/portal/v1/**` + `/api/admin/v1/**`
- 排除 `/internal/**`（机器调用）+ `/actuator/**`（健康检查）+ `/api/portal/v1/auth/**`（登录注册不限流）+ `/api/admin/v1/auth/**`

**理由**：登录注册被限会卡住合法用户；健康检查需要稳定通过。

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Backend 多实例下令牌桶不一致，限流可能放过 N 倍流量 | Phase 2a 接受（实例数有限，spec 已写入"多副本精度损失"边界）；Phase 2b Edge 主限流弥补 |
| 日/月计数器在 Backend 多副本下各自独立，可能放过 N × daily_limit | Phase 2a 接受（spec 显式声明上界）；Phase 2b Redis Counter 共享后归零 |
| LongAdder 进程崩溃丢失计数 → 重启后 DB 暖启动恢复 | DB 暖启动是设计的一部分，可接受 |
| 雪花 ID 在多实例下需机器位分配 | 本期把 `SnowflakeIdWorker` 改为 Spring Bean，workerId/dataCenterId 从 `ServiceProperties.snowflake.*` 读取，环境变量覆盖；启动时若值缺失抛错（不允许 Phase 1 默认构造器的随机回退在生产生效，避免副本 ID 冲突造成主键重复） |
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

- [ ] Internal API key 怎么轮换？双 key（current + previous）热切换的具体实现，Phase 2b 设计 Edge 配置热更新时一并解决；Phase 5 用 mTLS 彻底替代
- [ ] 新租户注册时的 `t_tenant_quota` 创建是否应该用数据库触发器 + 应用层兜底（双保险），还是仅靠 `AuthFacadeImpl` 事务？当前选后者（C5 决策），评审通过即定。
- [ ] 日志查询返回字段是否需要脱敏 client_ip（GDPR）？默认全量返回，Phase 5 视监管要求加脱敏开关
- [ ] 套餐删除是否级联？当前设计：仅 status=DISABLED 软禁用，不允许删除已被租户绑定的套餐（返回 409）