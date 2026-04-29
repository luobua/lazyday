## Why

Phase 1 已交付租户/AppKey/鉴权基础设施，Phase 2a 已交付 Backend 配额与调用日志域和进程内限流/采日志拦截器。平台目前缺三块运营能力：

1. **事件驱动外推**：租户暂停、AppKey 禁用、配额耗尽这类业务事件无法主动通知开发者，开发者只能轮询查询，体验差且延迟高。
2. **运营触达**：Phase 2a 里"配额耗尽邮件通知"这一项验收点未交付（当时 Backend 没有邮件通道，留给 Phase 3 一起做），目前用户超限只能通过 HTTP 429 感知，无法收到即时提醒。
3. **平台运营后台**：Phase 1 已为 Admin Console 建好 Next.js 骨架并留有 `/tenants` `/plans` 等页面 stub，但实际功能页面尚未实现，PLATFORM_ADMIN 仍需依赖 SQL/curl 操作租户与套餐。

Phase 3 把以上三块一次交付：Webhook 出站推送 + 邮件服务 + Admin 控制台首版。Phase 2b（Edge 网关 + WebSocket + Resilience4j + Frontend 配额仪表盘/CSV 导出）继续延后到 Phase 3 之后、Phase 4 之前串行启动——Webhook 是出站 HTTP，邮件是 SMTP，事件源全部位于 Phase 1+2a 已落地的领域内，不阻塞。

## What Changes

**Backend — Webhook 域**
- 新增 Flyway 迁移 `V4__init_webhook.sql`：`t_webhook_config`（订阅配置 + 事件类型多选 + HMAC 签名密钥）+ `t_webhook_event`（出站事件持久化 + 重试状态机）
- 新增 `WebhookConfig` 领域（聚合根 / Entity / Repository / PO + URL/事件类型/状态校验）
- 新增 `WebhookEvent` 领域（不创建聚合根——事件为不可变事实流；只 PO + Repository + 状态机：`pending → delivering → succeeded | failed | permanent_failed`）
- 新增 `DomainEventPublisher`：基于 Reactor `Sinks.Many<DomainEvent>`（`multicast().onBackpressureBuffer(10000)`），与 Phase 2a `CallLogWebFilter` 的异步写日志风格统一
- 新增 `WebhookSubscriber`：订阅 `DomainEventPublisher`，按租户的 `t_webhook_config` 物化为 `t_webhook_event` 行（status=pending）
- 新增 `WebhookDispatcher`（`@Scheduled` 5 秒间隔）：`SELECT ... FOR UPDATE SKIP LOCKED` 拉取到期事件，HMAC-SHA256 签名出站 POST，10 秒超时，按 `1m → 5m → 30m → 2h → 6h` 指数退避，5 次失败后置 `permanent_failed` 并触发邮件通知
- 新增 Webhook Portal API：`GET/POST/PUT/DELETE /api/portal/v1/webhooks`、`POST /api/portal/v1/webhooks/{id}/test`（独立单次发送，不入事件表，不计入重试统计）

**Backend — 邮件服务**
- 新增 Spring Mail 配置（阿里云邮件推送 SMTP 网关），密钥走环境变量 `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD`
- 新增 `EmailService`（异步发送：注册验证邮件、配额耗尽邮件、Webhook 永久失败邮件）
- 新增 Thymeleaf HTML 模板：`registration-verify.html` / `quota-exceeded.html` / `webhook-permanent-failed.html`
- **补齐 Phase 2a 漏的"配额耗尽邮件"验收项**：`RateLimitWebFilter` 在首次拒绝当日/当月请求时（同一租户 24h 去重），通过 `DomainEventPublisher` 发布 `QuotaExceededEvent`；新增 `QuotaExceededEmailSubscriber` 监听该事件并触发邮件，同时该事件也是 Webhook `quota.exceeded` 的事件源

**Backend — 事件类型清单**（v1）
- `appkey.disabled`：AppKey 禁用（Phase 1 AppKeyEntity 已有 disable 行为，本次接入事件发布）
- `appkey.rotated`：AppKey 轮换（同上）
- `tenant.suspended` / `tenant.resumed`：租户暂停/恢复（Phase 3 Admin 接口触发）
- `quota.exceeded`：配额耗尽（来源同上"补齐 Phase 2a"）
- `quota.plan_changed`：套餐变更（Admin 修改租户配额绑定时触发）

**Frontend — Portal Webhook 页面**
- 实现 `/webhooks` 页面（替换现有 stub）：列表 / 创建 / 编辑 / 删除 / 启停 / 测试推送
- 测试推送结果展示：HTTP 状态码 + 响应头 + 响应体（截断到 1KB）+ 耗时
- HMAC 签名密钥仅创建/轮换时显示一次
- `packages/api-client` 增加 `webhookApi`；`packages/types` 增加事件类型 / 配置 DTO

**Frontend — Admin Console 三页**
- 实现 `/tenants` 页面（替换现有 stub）：分页列表（搜索 + 状态筛选）+ 详情抽屉（租户元数据 + 当前套餐 + 当日/当月用量 + AppKey 数量）+ 暂停/恢复 + 修改套餐绑定（直接复用 Phase 2a `PUT /api/admin/v1/tenants/{tenantId}/quota`）
- 实现 `/plans` 页面（替换现有 stub）：套餐列表（含绑定租户数）+ 创建 / 编辑 / 软删除（直接复用 Phase 2a `/api/admin/v1/plans` API，纯前端实现）
- 实现 `/overview` 页面（增强现有页面）：总租户数 + 活跃租户数（最近 7 天有调用）+ 今日总调用量 + 今日成功率 + Top10 调用接口
- 复用 `packages/api-client` 现有 `adminApi`，仅扩展租户列表/详情/暂停接口

**Backend — Admin 租户管理 API**
- `GET /api/admin/v1/tenants`：分页 + 搜索（名称/邮箱）+ 状态筛选（ACTIVE / SUSPENDED）
- `GET /api/admin/v1/tenants/{id}`：租户详情（含套餐 + 用量 + AppKey 数）
- `POST /api/admin/v1/tenants/{id}/suspend` / `POST /api/admin/v1/tenants/{id}/resume`：状态变更（同时触发 `tenant.suspended` / `tenant.resumed` 事件）
- `GET /api/admin/v1/overview`：大盘指标聚合（来源 `t_tenant` + `t_call_log` + `t_app_key`）

**Backend — 启动配置**
- `LazydayApplication` 注入 `WebhookDispatcher` 调度任务，`@EnableScheduling` 已在 Phase 2a 启用
- `application.yaml` 新增 `service.webhook.*` 配置块（dispatch interval、HTTP 超时、最大重试、退避序列）+ `spring.mail.*` 配置块

## Capabilities

### New Capabilities

- `webhook-delivery`：Webhook 订阅 CRUD、领域事件发布、出站事件持久化与重试调度、HMAC 签名、测试推送、永久失败处理
- `email-notification`：Spring Mail + 阿里云 SMTP 配置、Thymeleaf 模板、注册验证 / 配额耗尽 / Webhook 永久失败三类邮件触发
- `admin-tenant-management`：Admin 租户列表 / 详情 / 暂停 / 恢复 API + Portal 概览仪表盘指标聚合
- `admin-plan-management`：Admin 套餐管理 UI 与既有 `/api/admin/v1/plans` API 的契约（API 在 Phase 2a 已实现，本 capability 只是把"绑定租户数"展示与前端 UI 行为成文）

### Modified Capabilities

- `quota-management`：补齐"配额耗尽邮件通知"验收项；新增"修改套餐绑定时发布 `quota.plan_changed` 事件"
- `traffic-control`：新增"`RateLimitWebFilter` 首次拒绝时发布 `QuotaExceededEvent`，同租户 24h 去重"

## Impact

- **数据库**：新增 2 张表 `t_webhook_config` / `t_webhook_event`（V4 迁移），`t_webhook_event` 不分区（出站事件量级显著低于 `t_call_log`，按 `next_retry_at` + `status` 索引检索即可）
- **Backend API**：新增 `/api/portal/v1/webhooks` (CRUD + test) + `/api/admin/v1/tenants` (list/detail/suspend/resume) + `/api/admin/v1/overview`
- **Backend 依赖**：新增 `spring-boot-starter-mail`、`spring-boot-starter-thymeleaf`（仅模板渲染，不接入 Web 视图层）；HTTP 出站走已有 `WebClient`，不引入新客户端
- **Backend 性能**：`DomainEventPublisher` 是进程内 Sinks，发布开销 < 100μs；`WebhookDispatcher` 每 5 秒一次定时拉取，单次拉取上限 100 条，对数据库压力可控
- **配置**：`service.webhook.dispatchIntervalSeconds`（默认 5）、`service.webhook.httpTimeoutMs`（默认 10000）、`service.webhook.maxRetries`（默认 5）、`service.webhook.backoffSequence`（默认 `60,300,1800,7200,21600`）+ `spring.mail.host` / `spring.mail.port` / `spring.mail.username` / `spring.mail.password` / `spring.mail.properties.mail.smtp.starttls.enable=true` + `service.email.from`（发件地址）
- **不变**：Phase 1 / Phase 2a 既有路由、迁移、Frontend 已实现页面（logs / credentials / overview Phase 1 版）均不受影响；Phase 2a 的 `RateLimitWebFilter` 仅新增"首次拒绝时调用 `DomainEventPublisher.publish`"一行，不改变限流判定逻辑
- **范围外（移交后续阶段）**：Edge 网关项目搭建、Edge GlobalFilter（限流/采日志）、WebSocket 双向通道、Resilience4j 熔断、Frontend 配额仪表盘、Frontend CSV 导出、Frontend Phase 4 AI 配置页面（rag / agent / workflow / brain-configs，stub 保留）