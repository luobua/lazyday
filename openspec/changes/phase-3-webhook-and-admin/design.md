## Context

Phase 1 已交付租户/用户/AppKey/鉴权和 Frontend Portal+Admin 双应用骨架；Phase 2a 已交付配额域、调用日志域、`RateLimitWebFilter`、`CallLogWebFilter`、`PartitionScheduler`、Admin 套餐 CRUD API。当前平台缺乏「主动通知」能力（开发者只能轮询），且 PLATFORM_ADMIN 仍需依赖 SQL 操作租户。Phase 3 在不引入 Edge / WebSocket / 消息队列等新基础设施的前提下，于现有 Reactive Spring Boot 单体内闭环 Webhook 出站推送、邮件通知和 Admin 控制台首版。

**关键约束**：

- 单体进程内运行，未来 Backend 可能扩到多副本；调度任务需要跨副本互斥
- 已有事件源：AppKey 禁用/轮换（Phase 1 AppKeyEntity 行为）、租户暂停/恢复（Phase 3 新增）、配额耗尽（Phase 2a `RateLimitWebFilter` 拒绝点）、套餐变更（Phase 2a `QuotaFacade` 写路径）
- Phase 2a 已统一使用 Reactor Sinks 处理异步写日志；Phase 3 Webhook 事件总线选用同样模式以保持心智模型一致
- 邮件密钥不入仓库，走环境变量；阿里云邮件推送支持标准 SMTP，无需引入云厂商 SDK

## Goals / Non-Goals

**Goals:**

- 业务事件触发后，开发者配置的回调地址在 10 秒内（首次尝试）收到带 HMAC-SHA256 签名的 POST 请求
- 推送失败按 `1m → 5m → 30m → 2h → 6h` 指数退避自动重试，5 次失败后置 `permanent_failed` 并发邮件通知租户
- PLATFORM_ADMIN 通过 Admin Console 完成「查看租户、暂停/恢复、改套餐、看大盘」全部日常运营，无需 SQL
- 租户超出当日/当月配额时，触发 `quota.exceeded` Webhook 事件 + 邮件通知（同一租户 24h 内只发一次邮件，避免轰炸）
- 重启 Backend 后未投递完成的事件 SHALL 继续投递（事件已持久化）
- 调度任务在多副本部署时不出现并发投递同一事件（`SELECT ... FOR UPDATE SKIP LOCKED` 行级锁）

**Non-Goals:**

- 不实现 Webhook 入站 webhook 接收（仅出站）
- 不实现事件类型动态注册（事件类型清单写死在 enum 中，新增事件需要发版）
- 不接入消息队列（Kafka/RabbitMQ/Redis Streams）；调度走 `@Scheduled` + DB 轮询足以支持 Phase 3 量级
- 不实现邮件模板的可视化编辑（模板写在 `resources/templates/email/*.html`）
- 不实现 Admin Console 的角色/权限分级（沿用 Phase 1 的单一 `PLATFORM_ADMIN` 角色）
- 不引入 Resilience4j（推迟到 Phase 2b 与 Edge 一同引入，Phase 3 Webhook 出站只用 `WebClient` 自带超时）
- 不实现 Edge 网关、WebSocket 通道、Frontend 配额仪表盘、Frontend CSV 导出、Frontend Phase 4 AI 配置页面

## Decisions

### D1：领域事件总线 — Reactor `Sinks.Many` 进程内多播

**选择**：`Sinks.many().multicast().onBackpressureBuffer(10000)`，由 `DomainEventPublisher` 单例持有；`WebhookSubscriber` 与 `QuotaExceededEmailSubscriber` 各自 `subscribe()`。

**理由**：
- 与 Phase 2a `CallLogWebFilter` 异步写日志的实现风格一致（同样是 Sinks），心智模型统一
- 完全 Reactive，不阻塞调用方线程
- 无需引入 Spring `ApplicationEventPublisher` 的同步发布异步监听二段式设计（更复杂、容易踩到事务回滚后事件已发出的坑）

**备选**：
- Spring `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`：能保证事务边界一致性，但会强耦合 Spring 事件机制，对于 Reactive 链路也有额外的线程切换开销
- 直接同步写 `t_webhook_event`：发布点零延迟，但所有发布点都要承担一次 INSERT，影响 RateLimitWebFilter 这种关键路径

**事务边界处理**：发布点显式在业务事务提交后再调用 `publish(...)`（用 R2DBC `transactionalOperator.execute(...).then(Mono.fromRunnable(publish))`）。**约束**：发布失败（buffer 满）SHALL 仅日志告警，不回滚业务事务——事件总线是 best-effort，但事件物化到 `t_webhook_event` 后才进入「保证投递」语义。

### D2：事件物化 — `WebhookSubscriber` 接收 Sinks 事件 → 写 `t_webhook_event`

**选择**：`WebhookSubscriber` 订阅 `DomainEventPublisher`，对每个事件 fan-out 到所有匹配该事件类型的 `t_webhook_config` 行，每个 `(event, config)` 组合写一条 `t_webhook_event`，`status=pending`、`next_retry_at=now()`。

**理由**：写入即「保证投递」起点；`WebhookDispatcher` 只读 `t_webhook_event` 即可，事件总线只负责领域 → 物化的瞬时通路，重启不丢正是因为物化后落库。

**死信策略**：物化失败（DB 不可达）→ 记录 ERROR + 计数器 `lazyday.webhook.materialize.failed`；不重试物化（发布点已经是异步，业务请求早已返回；物化失败属于运维告警范畴）。

### D3：投递调度 — `@Scheduled` + `SELECT ... FOR UPDATE SKIP LOCKED`

**选择**：
- `WebhookDispatcher.dispatch()` 每 5 秒触发一次，`SELECT id FROM t_webhook_event WHERE status='pending' AND next_retry_at <= now() ORDER BY next_retry_at LIMIT 100 FOR UPDATE SKIP LOCKED`
- 取到的事件批量更新为 `status='delivering'`、`locked_at=now()`、`locked_by=<instance_id>`，再异步发起 `WebClient` 调用
- 每次 dispatch 严格限 100 条，避免内存爆 + 留 headroom 给其它副本

**理由**：
- `FOR UPDATE SKIP LOCKED` 是 PostgreSQL 9.5+ 原生支持，多副本部署直接互斥，不需要外部分布式锁
- 5 秒间隔保证「事件触发后 10 秒内首次尝试」可达成（最坏：触发时刚错过本次 dispatch，等 5 秒下一轮 + 1-2 秒 HTTP）
- 100 条上限对应单副本 20 QPS 出站速率（5s/100 ≈ 20rps），Phase 3 量级下不会成为瓶颈

**幽灵锁恢复**：`status='delivering'` 但 `locked_at < now() - 60s` 的行视为僵死（dispatcher 进程崩溃丢锁），下一轮 dispatch 重置为 `pending` 后重新拉取。

### D4：HTTP 出站 — `WebClient` + 10 秒超时 + HMAC-SHA256 签名

**选择**：
- 复用现有 `WebClient.Builder`（Phase 1 已用于内部 API 调用），`responseTimeout(Duration.ofSeconds(10))`
- 每次发送在请求头加：
  - `X-Lazyday-Event-Id`（雪花 ID）
  - `X-Lazyday-Event-Type`（如 `quota.exceeded`）
  - `X-Lazyday-Timestamp`（epoch seconds）
  - `X-Lazyday-Signature`（`hmac_sha256(secret, timestamp + "." + body)` 的 hex）
  - `User-Agent: lazyday-webhook/1.0`
- 签名密钥（`secret`）在创建/轮换 Webhook 配置时由 Backend 生成 32 字节随机串，AES 加密存储（复用 Phase 1 `AESCryptoUtil`），仅创建时返回明文一次

**理由**：HMAC 签名格式与 Phase 1 Open API HMAC 鉴权一致，开发者验签代码可复用；10 秒超时是工业界 Webhook 默认值（Stripe / GitHub 都用），既给了慢端点机会，也避免长尾占用调度槽位。

**响应判定**：HTTP 2xx → `succeeded`；其它 status code（含 5xx）或网络错误 → `failed`，置下次重试时间。

### D5：重试退避 — `1m → 5m → 30m → 2h → 6h`，5 次失败 → `permanent_failed` + 邮件

**选择**：定值序列写在 `service.webhook.backoffSequence` 配置里（默认 `60,300,1800,7200,21600`），`retry_count` 从 0 起，第 N 次失败后 `next_retry_at = now() + backoffSequence[retry_count]`，`retry_count` 达到 `maxRetries`（默认 5）后置 `permanent_failed`。

**理由**：定值序列易理解、易调试、易在客户端复现；总等待时间 ≈ 8.6 小时，对临时故障容忍充分。

**permanent_failed 处理**：
- 触发 `WebhookPermanentFailedEvent`（再走一次事件总线 → `EmailService`）发邮件给租户的 admin user
- 邮件内容包含：事件 ID、事件类型、回调 URL、最后一次 HTTP 响应码 / 错误摘要、Webhook 配置 ID（带 deeplink 到 Portal `/webhooks?id=...`）

### D6：Webhook 测试推送 — 独立链路、不入事件表

**选择**：`POST /api/portal/v1/webhooks/{id}/test` 直接构造一个固定形态的测试 payload（`event_type="webhook.test"`），用相同的 `WebClient` + 签名逻辑同步发送一次（`Mono.block` 在 Reactive 链路里不可，改为 `flatMap` 异步等待结果），返回 HTTP 状态码、响应头摘要、响应体（截断 1KB）、耗时。

**理由**：避免污染事件表的统计（成功率 / 重试次数），也不被重试调度拉起；语义上 test 是「探针」，不是真实事件。

### D7：邮件去重 — 配额耗尽 24h / 租户

**选择**：在 `RateLimitWebFilter` 拒绝路径里维护 `Caffeine<TenantId, Long>` 缓存（key = tenantId, value = 上次发邮件 epoch ms，TTL=24h, max=10000）；只有当缓存中无该 tenant 或上次发邮件已超过 24h 才发布 `QuotaExceededEvent`。

**理由**：超限场景下被拒绝的请求可能每秒上百条，没有去重会发上百封同样的邮件。Webhook 走 24h 去重的同样窗口（同一租户、同一事件类型、24h 内只物化一条 `t_webhook_event`）。

**实现位置**：`DomainEventPublisher` 内置「事件去重过滤器」（基于 Caffeine），由调用方传入 dedup key (`tenantId:eventType`) 与 TTL；`RateLimitWebFilter` 调用时传入 24h，AppKey 禁用等单点事件不传 dedup（每次都发）。

### D8：Admin 控制台 — 复用 Phase 2a Admin API + 新增三个端点

**选择**：
- `/plans` 页面纯前端实现，复用 Phase 2a `/api/admin/v1/plans` API，仅新增"绑定租户数"列（后端 `AdminPlanResponse` 增加 `bindingCount` 字段，按 `t_tenant_quota.plan_id` count）
- `/tenants` 页面新增 Backend `/api/admin/v1/tenants` (list / detail / suspend / resume) API
- `/overview` 页面新增 Backend `/api/admin/v1/overview` API：聚合 `t_tenant`、`t_call_log`、`t_app_key` 三表
- `t_tenant.status` 字段在 Phase 1 已存在 (`ACTIVE` / `SUSPENDED`)，suspend/resume 仅做状态翻转
- 暂停后租户的有效行为：JWT 仍能通过鉴权（不强制立即下线，避免误触发损害正常会话），但 `RateLimitWebFilter` 在 status=SUSPENDED 时直接拒绝（403 + `error_code: TENANT_SUSPENDED`）

**理由**：避免重复实现 Phase 2a 已存在的 API；suspend/resume 复用 `t_tenant.status` 而非新增字段；JWT 不强制下线降低误操作冲击面（管理员误暂停 → 恢复后立即可用）。

### D9：Webhook 配置数据模型

```sql
-- t_webhook_config
id BIGINT IDENTITY PK
tenant_id BIGINT NOT NULL              -- FK to t_tenant
name VARCHAR(100) NOT NULL             -- 用户起名
url VARCHAR(500) NOT NULL              -- 必须 https:// 开头（Phase 3 不允许 http）
event_types VARCHAR(500) NOT NULL      -- 逗号分隔事件类型集合
secret_encrypted VARCHAR(500) NOT NULL -- AES 加密的 HMAC 密钥
status VARCHAR(20) NOT NULL            -- ACTIVE / DISABLED
audit fields (created_by, created_time, updated_by, updated_time)
INDEX idx_webhook_config_tenant (tenant_id)
```

```sql
-- t_webhook_event
id BIGINT PK                            -- 雪花 ID
tenant_id BIGINT NOT NULL
config_id BIGINT NOT NULL               -- FK to t_webhook_config
event_type VARCHAR(50) NOT NULL
payload JSONB NOT NULL                  -- 事件 payload
status VARCHAR(20) NOT NULL             -- pending / delivering / succeeded / failed / permanent_failed
retry_count INT NOT NULL DEFAULT 0
next_retry_at TIMESTAMP NULL            -- pending 状态有效
locked_at TIMESTAMP NULL                -- delivering 状态有效
locked_by VARCHAR(100) NULL             -- instance id
last_http_status INT NULL
last_response_excerpt VARCHAR(1024) NULL
last_error VARCHAR(500) NULL
created_time TIMESTAMP NOT NULL
delivered_time TIMESTAMP NULL
INDEX idx_webhook_event_dispatch (status, next_retry_at)
INDEX idx_webhook_event_tenant_created (tenant_id, created_time DESC)
```

**理由**：`t_webhook_event` 不分区——出站事件量级远低于 `t_call_log`，按 `(status, next_retry_at)` 索引检索效率足够；`payload` 用 JSONB 便于将来新增字段不改表结构；`locked_by` 留 instance id 便于排查幽灵锁来源。

### D10：邮件模板与发送

**选择**：
- `EmailService.send(toAddress, subject, templateName, model)` 异步接口；用 Reactor `Schedulers.boundedElastic()` 包装 Spring Mail（同步阻塞 API）
- 模板放 `resources/templates/email/`，Thymeleaf 渲染
- 发件方地址固定 `service.email.from`（默认 `noreply@lazyday.dev`，开发环境可覆盖）
- 阿里云邮件推送的限频（默认 100 封/秒）远高于 Phase 3 预期发送量，无需做客户端限流

**理由**：Spring Mail 是阻塞 API，必须 `boundedElastic` 调度避免阻塞 EventLoop；Thymeleaf 是 Spring 默认模板引擎，不引新依赖。

## Risks / Trade-offs

[**风险**] Webhook 接收方端点慢或挂死 → `WebClient` 10 秒超时即终止；调度槽位不被独占（每次只取 100，其它槽位还能给其它事件）。

[**风险**] 大批量 permanent_failed 引发邮件轰炸 → permanent_failed 邮件本身也走 `DomainEventPublisher`，但邮件订阅者对 (tenantId, configId) 24h 去重；同一 webhook config 24h 内最多发一封"permanent_failed"邮件。

[**风险**] 多副本场景下 `@Scheduled` 同时触发但 `SKIP LOCKED` 互斥失效 → 不会失效（PostgreSQL 行级锁是事务内的硬保证），但会有"空跑"（每个副本都跑 SQL 但只有一个能拿到锁），可接受。

[**风险**] `Sinks.Many` buffer 溢出（10000）→ 计数器告警，但事件丢失。**缓解**：buffer 大小可配；订阅者性能瓶颈监控（subscriber 滞后会触发 backpressure）；如未来量级上升，迁移到 Redis Streams 是平滑路径。

[**风险**] HMAC 签名密钥泄露 → Portal 提供「轮换 secret」操作（生成新 secret 替换旧的，旧的立即失效），轮换记入审计日志。

[**风险**] 事件总线在事务回滚后已发布 → 通过 D1 约束「事务提交后再 publish」缓解；但仍有边角情况（提交后进程崩溃，事件未物化）。**接受**：Phase 3 不追求 exactly-once，重要事件由消费侧（如 Webhook 接收方）做幂等（事件 ID 去重）。

[**权衡**] 不接入消息队列 → 简化部署，但跨进程语义变弱；当 Backend 扩到 5+ 副本时建议评估迁移到 Redis Streams 或 RabbitMQ。

[**权衡**] 邮件密钥从环境变量加载 → 不入仓库安全，但启动期校验需要在 `application.yaml` 给出 default 占位 + `EmailService` 启动时 ping SMTP 失败仅 WARN 不阻断（开发环境无 SMTP 凭证也能跑）。

[**权衡**] suspend 不强制下线 JWT → 误操作影响小，但「恶意租户」暂停后还能用现有 JWT 调用直到 token 过期（最多 2h）。Phase 3 接受此权衡；如需要立即下线，未来可在 JWT 验证时检查 `t_tenant.status`。

## Migration Plan

1. **DB 迁移**：编写 `V4__init_webhook.sql`，包含 `t_webhook_config` + `t_webhook_event`；本地 / dev / staging 分别执行 Flyway migrate；生产部署前在 staging 做一次完整回归（含订阅 + 触发 + 投递 + 重试）。
2. **配置注入**：生产环境新增 `SPRING_MAIL_HOST` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` / `SERVICE_WEBHOOK_*` 环境变量；本地开发提供 `application-local.yaml.example` 示例。
3. **Phase 2a 配额耗尽邮件回填**：升级后第一次配额耗尽即触发邮件，无需历史回填。
4. **Frontend 部署**：`/webhooks` `/tenants` `/plans` `/overview` 由 stub 替换为完整页面；旧 stub 页面无 cookie / state 残留，零迁移成本。
5. **回滚策略**：
   - Backend 回滚 → 仅需将 `WebhookDispatcher` 调度任务关闭（`service.webhook.dispatchEnabled=false`）即可停止出站；`t_webhook_event` 保留待后续重投或人工清理。Flyway 反向迁移不做（按团队约定不写 down migration），如必须，DBA 手动 `DROP TABLE`。
   - Frontend 回滚 → Next.js 应用回滚到上一版本即可，stub 重新生效。
6. **灰度策略**：默认 `service.webhook.dispatchEnabled=true`，但配置表为空时 `WebhookDispatcher` 一次循环为空跑（`SELECT` 返回 0 行），无副作用；可先开放给少数租户配置 webhook 验证后再宣布功能可用。

## Open Questions

- **OQ1**：阿里云邮件推送的子账号 / 发信地址是否需要业务方在 Phase 3 启动前申请？（建议在 tasks.md 第一项前置确认）
- **OQ2**：Webhook 测试推送是否需要 IP 白名单 / 防 SSRF？Phase 3 暂只校验 `https://` 和域名格式（拒绝 `localhost`、`127.0.0.0/8`、`10.0.0.0/8`、`172.16.0.0/12`、`192.168.0.0/16`、`169.254.0.0/16`），是否还需更严格策略？（默认不再加严，留 OQ）
- **OQ3**：Admin 控制台的"暂停租户"是否需要二次确认弹窗 + 操作原因记录？（建议加二次确认 + 选填原因记 `t_audit_log`，但 `t_audit_log` 表暂未建，本期先不记录、留 Phase 5 完善）
- **OQ4**：Frontend 已存在的 Phase 4 stub 页面（rag / agent / workflow / brain-configs）是否在 Phase 3 借机统一调整 sidebar 隐藏 / 灰显？（建议 Phase 3 暂不动，Phase 4 一起处理）