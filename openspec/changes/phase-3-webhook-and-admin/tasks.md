## 0. 启动前置确认

- [x] 0.1 与业务方确认阿里云邮件推送子账号 / 发件域名（OQ1），获取 SMTP host/port/username/password 与发件地址 `service.email.from`
- [x] 0.2 配置 staging / dev 环境变量 `SPRING_MAIL_HOST` `SPRING_MAIL_PORT` `SPRING_MAIL_USERNAME` `SPRING_MAIL_PASSWORD` `SERVICE_EMAIL_FROM`
- [x] 0.3 在 `application-local.yaml.example` 增加 SMTP + Webhook 配置示例，注明本地开发可不配置 SMTP（自动 skip 发送）

## 1. 数据库迁移 V4

- [x] 1.1 编写 `backend/src/main/resources/db/migration/V4__init_webhook.sql`：`t_webhook_config` 表（id/tenant_id/name/url/event_types/secret_encrypted/status/audit + idx_webhook_config_tenant）
- [x] 1.2 同一脚本追加 `t_webhook_event` 表（id/tenant_id/config_id/event_type/payload jsonb/status/retry_count/next_retry_at/locked_at/locked_by/last_http_status/last_response_excerpt/last_error/created_time/delivered_time）
- [x] 1.3 同一脚本追加 `(status, next_retry_at)` 与 `(tenant_id, created_time DESC)` 索引
- [x] 1.4 验证 PostgreSQL JSONB + `FOR UPDATE SKIP LOCKED` 语法在迁移脚本中无误
- [ ] 1.5 本地 `./mvnw spring-boot:run` 验证 Flyway 自动执行 V4 成功，`\d t_webhook_*` 检查表与索引

## 2. Backend — 领域事件总线

- [x] 2.1 在 `domain/event/` 包下定义 `DomainEvent`（密封接口）+ `AppKeyDisabledEvent` / `AppKeyRotatedEvent` / `TenantSuspendedEvent` / `TenantResumedEvent` / `QuotaExceededEvent` / `QuotaPlanChangedEvent` / `WebhookPermanentFailedEvent` 七个事件类型 record
- [x] 2.2 在 `infrastructure/event/` 实现 `DomainEventPublisher`：`Sinks.Many<DomainEvent>` `multicast().onBackpressureBuffer(10000)`，提供 `publish(DomainEvent)` 与 `asFlux()` API
- [x] 2.3 实现 `DomainEventDeduplicator`：基于 Caffeine（`expireAfterWrite=24h`、`maximumSize=10000`），提供 `tryRecord(key)` 返回 boolean
- [x] 2.4 单测：`Sinks` buffer 满时 `EmitResult.FAIL_OVERFLOW` 路径 + 多订阅者 fanout + dedup 24h 窗口
- [x] 2.5 接入 Phase 1 / Phase 2a 现有发布点：`AppKeyEntity.disable()` / `.rotate()` 提交后发布事件（用 `transactionalOperator` 包装）

## 3. Backend — Webhook 域

- [x] 3.1 创建 `domain/webhookconfig/`：`WebhookConfigPO`（@Table("t_webhook_config")）+ `WebhookConfigEntity` + `WebhookConfigAggregation`（含 URL 校验、event_types 解析）+ `WebhookConfigRepository`
- [x] 3.2 创建 `domain/webhookevent/`：`WebhookEventPO`（@Table("t_webhook_event")）+ `WebhookEventRepository`（含 `selectDueForDispatch()`、`updateToDelivering()`、`updateToFailedForRetry()`、`updateToSucceeded()`、`updateToPermanentFailed()`、`recoverGhostLocks()` 方法）
- [x] 3.3 实现 `application/facade/WebhookFacade` + impl：CRUD（含 URL 私有网段拦截 + secret 生成与 AES 加密复用 `AESCryptoUtil`）+ rotate-secret + test-push（构造测试 payload，独立 `WebClient` 调用，不写事件表）
- [x] 3.4 实现 `application/service/WebhookSubscriber`：在 `@PostConstruct` 订阅 `DomainEventPublisher.asFlux()`，对每个事件查 active 配置 fan-out 写 `t_webhook_event`（status=pending, next_retry_at=now()）
- [x] 3.5 实现 `infrastructure/scheduler/WebhookDispatcher`：`@Scheduled(fixedDelay=service.webhook.dispatchIntervalSeconds*1000)`，每轮 `recoverGhostLocks()` → `selectDueForDispatch(limit=100)` → 批量 `updateToDelivering()` → 异步 `WebClient` 调用 → 根据响应 `updateToSucceeded()` 或 `updateToFailedForRetry()`（达到 maxRetries 则 `updateToPermanentFailed()` 并发 `WebhookPermanentFailedEvent`）
- [x] 3.6 实现 `WebhookSigner`：`HMAC-SHA256(secret, timestamp + "." + body)` → hex
- [x] 3.7 实现 `interfaces/handler/PortalWebhookHandler`（@RequestMappingPortalV1）：list / get / create / update / delete / rotate-secret / test 七个端点
- [x] 3.8 添加 `service.webhook.*` 配置项到 `ServiceProperties` 与 `application.yaml`
- [ ] 3.9 集成测试：本地起一个 nginx echo 服务做 webhook 接收方，触发 AppKey 禁用 → 端点收到带签名的 POST → 验证签名头格式

## 4. Backend — 邮件服务

- [x] 4.1 添加 `spring-boot-starter-mail` 与 `spring-boot-starter-thymeleaf` 依赖到 `backend/pom.xml`（thymeleaf 仅用于邮件模板渲染，注意 WebFlux 默认 thymeleaf 配置不冲突）
- [x] 4.2 实现 `application/service/EmailService` + impl：`Mono<Void> send(toAddresses, subject, templateName, model)`，内部 `Schedulers.boundedElastic()` 包装 Spring `JavaMailSender.send`，未配置 SMTP 时直接 log warn 跳过
- [x] 4.3 实现 `infrastructure/email/EmailTemplateRenderer`：用 `ITemplateEngine`（Thymeleaf）渲染 `templates/email/*.html` 到字符串
- [x] 4.4 创建 `resources/templates/email/registration-verify.html` / `quota-exceeded.html` / `webhook-permanent-failed.html` 三个 Thymeleaf 模板，含中文文案 + Lazyday 头部品牌
- [x] 4.5 实现 `application/service/QuotaExceededEmailSubscriber`：订阅 `DomainEventPublisher`，按 (tenantId, period) 24h 去重，查租户的 TENANT_ADMIN user emails 调用 `EmailService.send`
- [x] 4.6 实现 `application/service/WebhookPermanentFailedEmailSubscriber`：订阅 `DomainEventPublisher`，按 (tenantId, configId) 24h 去重发邮件
- [x] 4.7 修改 `AuthFacadeImpl.register`：注册成功提交事务后发 registration-verify 邮件（异步、失败 warn 不回滚）
- [x] 4.8 实现 `interfaces/handler/PortalAuthHandler.verifyEmail`：`GET /api/portal/v1/auth/verify-email?token=xxx`，token 24h 有效（用 JWT 携带 userId + 用途）
- [x] 4.9 单测：dedup 路径、SMTP 不可达时 skip 路径、模板渲染快照测试

## 5. Backend — Phase 2a 配额耗尽事件接入

- [x] 5.1 修改 `infrastructure/filter/RateLimitWebFilter`：拒绝 daily/monthly 路径上调用 `DomainEventPublisher.publish(QuotaExceededEvent)`（用 `DomainEventDeduplicator` 24h 去重）
- [x] 5.2 修改 `RateLimitWebFilter`：在 QPS / quota 评估之前增加 `t_tenant.status='SUSPENDED'` 检查（缓存 30 秒），SUSPENDED 直接 403 + `TENANT_SUSPENDED`
- [x] 5.3 修改 `application/facade/QuotaFacadeImpl.updateTenantQuota`：成功 commit 后发 `QuotaPlanChangedEvent`（previous_plan_id == new_plan_id 也发，但完全相同的请求 body 检测幂等并跳过）
- [x] 5.4 单测：单租户连续超限 100 次只发布 1 次事件、悬停 24h 后再次触发会发布、SUSPENDED 租户 403 路径

## 6. Backend — Admin 租户管理

- [x] 6.1 实现 `application/facade/AdminTenantFacade` + impl：`listTenants(query)` / `getTenantDetail(id)` / `suspendTenant(id)` / `resumeTenant(id)` / `getOverview()`
- [x] 6.2 实现 `interfaces/handler/AdminTenantHandler`（@RequestMappingAdminV1）：`GET /tenants`（分页 + keyword + status filter）/ `GET /tenants/{id}` / `POST /tenants/{id}/suspend` / `POST /tenants/{id}/resume` / `GET /overview`
- [x] 6.3 修改 `domain/tenant/repository/TenantRepository`：增加 `findPage(keyword, status, pageable)` 与 `findDetailWithQuotaAndUsage(id)`（联表 `t_tenant_quota` + `t_quota_plan` + 当日/当月聚合）
- [x] 6.4 实现 `application/facade/QuotaFacadeImpl.getOverviewMetrics()`：`total_tenants` / `active_tenants_7d` / `today_calls` / `today_success_rate` / `top_paths_today` 五个指标聚合（可走 `t_call_log` 单次 SQL UNION）
- [x] 6.5 修改 `domain/quotaplan/repository/QuotaPlanRepository.findAll`：在返回 DTO 中带 `binding_count`（左连 `t_tenant_quota` GROUP BY plan_id）
- [x] 6.6 修改 `AdminPlanHandler` 返回结构带 `binding_count`
- [ ] 6.7 集成测试：list / detail / suspend / resume / overview 五个端点端到端，含权限拒绝（非 PLATFORM_ADMIN 返回 403）

## 7. Frontend — Webhook Portal 页面

- [x] 7.1 在 `frontend/packages/types` 增加 `WebhookConfig` / `WebhookEventType` / `WebhookCreateRequest` / `WebhookTestResult` 等类型
- [x] 7.2 在 `frontend/packages/api-client` 增加 `webhookApi`：list / get / create / update / delete / rotateSecret / test
- [x] 7.3 在 `frontend/apps/portal/hooks` 增加 `use-webhooks.ts`（TanStack Query 包装 webhookApi）
- [x] 7.4 重写 `frontend/apps/portal/app/(dashboard)/webhooks/page.tsx`：列表 Table + 创建 Modal（事件类型多选 + URL 输入）+ 编辑 Modal + 删除确认 + 启停 + 测试推送 Modal（展示状态码 / 响应头 / 响应体 / 耗时）
- [x] 7.5 实现 secret 一次显示弹窗（创建成功 + 轮换成功后 Modal，复制按钮 + 关闭即不再可见）
- [x] 7.6 表单校验：URL 必须 https + 事件类型至少选一个

## 8. Frontend — Admin 租户管理页面

- [x] 8.1 在 `frontend/packages/types` 增加 `AdminTenantSummary` / `AdminTenantDetail` / `AdminOverviewMetrics` 类型
- [x] 8.2 在 `frontend/packages/api-client` 增加 `adminTenantApi`：list / detail / suspend / resume / overview
- [x] 8.3 在 `frontend/apps/admin/hooks` 增加 `use-admin-tenants.ts` 与 `use-admin-overview.ts`
- [x] 8.4 重写 `frontend/apps/admin/app/(dashboard)/tenants/page.tsx`：分页 Table + 搜索框 + 状态筛选 + 详情抽屉 + 暂停/恢复确认 Modal + 改套餐 Modal（选套餐 + 自定义覆盖输入 → `PUT /api/admin/v1/tenants/{id}/quota`）
- [x] 8.5 重写 `frontend/apps/admin/app/(dashboard)/overview/page.tsx`：5 个指标卡 + Top10 接口表格（首版可不加 ECharts，纯数字+表格）

## 9. Frontend — Admin 套餐管理页面

- [x] 9.1 在 `frontend/packages/api-client/adminApi` 调整 `getPlans` 返回结构，包含 `binding_count`
- [x] 9.2 重写 `frontend/apps/admin/app/(dashboard)/plans/page.tsx`：Table（含 binding_count 列）+ 新建/编辑 Modal + 禁用确认（含 binding_count > 0 的错误提示）
- [x] 9.3 校验表单：limits 必须 > 0，max_app_keys 接受 -1 表示无限

## 10. 端到端联调

- [ ] 10.1 起本地 PostgreSQL + Backend + Portal + Admin，完整跑一遍 V4 迁移 + 三表数据正确
- [ ] 10.2 Portal 创建 Webhook 订阅 `appkey.disabled` → Admin 暂停某 AppKey（或 Portal 禁用）→ nginx echo 端点收到 POST + 签名验证通过
- [ ] 10.3 触发租户配额耗尽 → 收到 `quota.exceeded` Webhook + 邮件（用 mailhog 或临时 SMTP 验证）
- [ ] 10.4 Webhook 接收方故意返回 500 5 次 → 第 5 次失败后状态变 `permanent_failed`，租户 admin 邮箱收到 permanent_failed 邮件
- [ ] 10.5 Admin 暂停一个租户 → 该租户 Open API 调用立即（30 秒内）返回 403 TENANT_SUSPENDED
- [ ] 10.6 Admin Console 大盘指标与数据库聚合手算结果一致

## 11. 验收门

- [ ] 11.1 验收：Webhook 事件触发后回调地址 10 秒内收到带签名 POST
- [ ] 11.2 验收：推送失败按 `1m → 5m → 30m → 2h → 6h` 重试，5 次失败 permanent_failed
- [ ] 11.3 验收：Admin Console 可查看所有租户列表、详情、暂停/恢复
- [ ] 11.4 验收：暂停后租户 Open API 30 秒内返回 403
- [ ] 11.5 验收：Admin 改套餐 → 租户配额实时生效（30 秒内）
- [ ] 11.6 验收：配额耗尽触发 `quota.exceeded` Webhook + 邮件到 TENANT_ADMIN 邮箱（24h 内只发一次）
- [ ] 11.7 验收：注册成功后收到 registration-verify 邮件
- [ ] 11.8 验收：`./mvnw clean test` 全部通过；`pnpm -C frontend build` 全部通过

## 12. 文档与归档

- [x] 12.1 更新 `docs/iteration-plan.md`：在修订记录追加 v0.4 条目，注明 Phase 3 完成 + Phase 2b 仍待启动 + V4 已使用
- [x] 12.2 更新 `docs/backend-architecture.md`：Webhook 数据流图 + 邮件服务说明
- [ ] 12.3 准备 OpenSpec archive：完成后运行 `/opsx:archive phase-3-webhook-and-admin`
