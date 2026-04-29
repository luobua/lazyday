## Why

Phase 1 已交付租户/AppKey/鉴权基础设施。Phase 2 原始范围（6-8 周）覆盖 Backend 配额域 + 调用日志域 + Edge 网关 + Edge GlobalFilter（限流/日志）+ WebSocket 双向通道 + Frontend 日志页/统计看板，端到端依赖较多，整段交付周期长、联调阻塞面大。

切分为 Phase 2a（仅 Backend）与 Phase 2b（Edge + Frontend + WebSocket）后：

- Phase 2a 在不依赖 Edge 项目搭建的前提下，单独把"配额数据模型 + 管理 API + 调用日志写入与查询 + 自身进程内的限流/采日志拦截"闭环，先让平台具备**计费基础**和**直连兜底防护**。
- Phase 2b 再交付 Edge GlobalFilter（主限流）+ WebSocket 通道 + Frontend 可视化。

工作目录中已经存在一批未提交的 Phase 2a 雏形文件（V3 迁移、quotaplan/tenantquota/calllog 域、Backend 侧 RateLimit/CallLog WebFilter、PartitionScheduler、Portal/Admin/Internal API），本提案以这一批草稿为参考蓝图，但**重新走 OpenSpec 流程**——先冻结规格与设计，再按提案 tasks 重新实施（必要时复用代码、必要时调整）。

## What Changes

**Backend — 配额域**
- 新增 Flyway 迁移 `V3__init_quota_and_log.sql`：`t_quota_plan` 套餐模板表 + `t_tenant_quota` 租户配额实例表 + 默认套餐种子（Free/Pro/Enterprise）
- 新增 QuotaPlan 域（QuotaPlanAggregation / QuotaPlanEntity / QuotaPlanRepository / QuotaPlanPO）
- 新增 TenantQuota 域（TenantQuotaAggregation / TenantQuotaEntity / TenantQuotaRepository / TenantQuotaPO）
- 新增 QuotaFacade（套餐 CRUD + 租户配额绑定 + Admin 自定义覆盖 + 有效配额计算）
- 配额生效策略：`effective_qps = COALESCE(custom_qps, plan.qps)`，套餐变更与覆盖修改实时生效（不缓存写路径）

**Backend — 调用日志域**
- 同一 Flyway 迁移 `V3` 中追加 `t_call_log` 按月分区主表 + 当月 + 下月分区 + 联合索引
- 新增 CallLog 域（CallLogPO + CallLogRepository，**不创建 Aggregation**——日志为不可变事实流，无业务行为）
- 新增 CallLogFacade（写入 + 分页查询 + 统计聚合）
- ID 策略：雪花算法（应用层生成，非自增）
- 新增 PartitionScheduler：每月最后一天 02:00 创建下月分区（防止跨月写入失败）

**Backend — 进程内拦截器**
- 新增 `RateLimitWebFilter`：解析当前请求的 tenant（来自 JWT 或后续 Edge 注入的 header），按 `t_tenant_quota` 有效 QPS/日/月限额做内存令牌桶 + 数据库累计校验，超限返回 HTTP 429
- 新增 `CallLogWebFilter`：在响应完成后异步写入 `t_call_log`（fire-and-forget + 失败仅记日志，不阻塞响应）
- 两个 WebFilter 仅作用于 Open API + Portal/Admin API 子集（通过路径前缀过滤），明确**作为 Edge 上线前的入口拦截 + Edge 上线后的回源兜底**（在 design.md 中写明）

**Backend — Portal/Admin/Internal API**
- Portal：`GET /api/portal/v1/quota`（当前租户的有效配额 + 当日/当月用量）
- Portal：`GET /api/portal/v1/logs`（分页查询本租户调用日志，支持时间范围 + 状态码筛选）
- Portal：`GET /api/portal/v1/logs/stats`（按小时/天聚合，返回调用量 + 成功率）
- Admin：`GET/POST/PUT/DELETE /api/admin/v1/plans`（套餐 CRUD）
- Admin：`PUT /api/admin/v1/tenants/{tenantId}/quota`（绑定套餐 + 自定义覆盖）
- Internal：`GET /internal/quota/effective?tenantId=`（供 Phase 2b Edge 调用）

**Backend — 启动配置**
- `LazydayApplication` 启用 `@EnableScheduling` 让 PartitionScheduler 工作

## Capabilities

### New Capabilities

- `quota-management`：套餐 CRUD + 租户配额绑定 + 自定义覆盖 + 有效配额计算
- `call-logging`：按月分区写入 + 分页查询 + 聚合统计 + 月度分区自动维护
- `traffic-control`：Backend 进程内 QPS/日/月限流 + 调用日志采集（暂代未来 Edge GlobalFilter 的角色）

### Modified Capabilities

无（均为新建，不动 Phase 1 已 archive 的 spec）。

## Impact

- **数据库**：新增 3 张表（`t_quota_plan` / `t_tenant_quota` / `t_call_log` 含月度分区），需保证 PostgreSQL 已就绪；分区策略要求 PostgreSQL ≥ 11
- **Backend API**：新增 `/api/portal/v1/{quota,logs,logs/stats}` + `/api/admin/v1/{plans,tenants/{id}/quota}` + `/internal/quota/effective`
- **请求路径性能**：所有命中 Open/Portal/Admin API 的请求会经过 RateLimitWebFilter（内存读 + 偶发 DB 计数） + CallLogWebFilter（异步写）。设计目标：单次过滤器开销 P99 ≤ 5ms
- **配置**：`ServiceProperties` 新增 `internalContextPath` 属性 + Internal API 注解（`@RequestMappingInternal`，仅信任 K8s 内网/Sidecar 调用，不走 JWT）
- **依赖**：新增 Bucket4j（Java 限流库，纯内存版）；不引入 Redis 依赖（Redis 限流推迟到 Phase 2b Edge）
- **不变**：现有 Phase 1 路由 / Frontend / Edge 项目均不受影响
- **范围外（移交 Phase 2b）**：Edge 网关项目搭建、Edge GlobalFilter、WebSocket 通道、Resilience4j 熔断、Frontend 日志查询页/统计看板/CSV 导出