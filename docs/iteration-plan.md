# Lazyday 开放平台 — 开发迭代计划

> 版本：v0.2
> 日期：2026-04-26
> 作者：fanqibu

---

## 修订记录

| 版本 | 日期 | 修订内容 |
|------|------|---------|
| v0.2 | 2026-04-26 | Phase 1 补充 Refresh Token + CSRF + `@RequestMappingOpenV1` + Admin 验收项；Phase 2 V2 脚本补充 t_call_log 分区表 + t_tenant_quota，新增 Resilience4j 熔断，CSV 导出移入 Phase 2；Phase 3 邮件服务对齐 requirements-design.md §7；Phase 4 技术依赖修正为「Phase 2-3」；Phase 5 V5 改为预聚合表，移除重复的 CSV 导出；新增团队规模假设与风险缓冲说明 |
| v0.1 | 2026-04-26 | 初始版本，基于 5 份架构文档划分 5 个迭代阶段 |

---

## 1. 项目现状

| 组件 | 状态 | 说明 |
|------|------|------|
| Backend 骨架 | ✅ 已完成 | DDD 分层结构、基础设施配置（DB、Flyway、R2DBC、AES/RSA/HMAC加密、Snowflake ID） |
| Backend 业务域 | ⚠️ 仅示例 | 只有 Demo + User 域（示例代码），缺租户域和 AppKey 域 |
| Flyway 迁移脚本 | ❌ 空目录 | `db/migration/` 无脚本，PostgreSQL 数据库未初始化 |
| Edge 网关 | ❌ 不存在 | 需要从零搭建 Spring Cloud Gateway 项目 |
| Frontend | ❌ 不存在 | `frontend/` 目录为空，需要从零搭建 Next.js Monorepo |

---

## 2. 迭代划分原则

1. **每个迭代端到端可运行**：前端 + 后端 + 网关，闭环一个用户场景
2. **依赖关系先行**：网关鉴权 → 限流 → 管理 API → AI 执行
3. **AI 能力后置**：AI 能力依赖 Phase 1-2 的网关和管理基础设施
4. **大脑独立交付**：Edge 大脑组装在 Phase 4 一次交付完整 AI 能力栈

---

## 3. 迭代详细计划

### Phase 1 — 基础开放平台（8-10 周）

**交付目标**：开发者可注册租户、创建 AppKey、通过 Open API 验证身份并调用基础管理接口

**技术依赖**：无（Phase 1 先行，无前置依赖）

**交付范围**：

**3.1.1 Backend 基础设施**
- 新增 Flyway 迁移脚本 `V1__init_tenant.sql`（t_user + t_tenant + t_app_key 表）
- 实现 Backend 租户域（TenantAggregation + TenantEntity + TenantRepository）
- 实现 Backend 用户域扩展（基于现有 User 域，补充 bcrypt 密码哈希 + JWT Access Token 2h + Refresh Token 7d + 记住我 30d）
- 实现 Backend AppKey 域（AppKeyAggregation + AppKeyEntity + AppKeyRepository + SecretKey AES 加密 + 轮换宽限期）
- 实现 Backend 鉴权接口（`@RequestMappingPortalV1` + `@RequestMappingAdminV1` + `@RequestMappingOpenV1` 注解）
- 实现 Backend 通用响应格式（code 数字 + error_code 字符串 + data + requestId）
- 实现 CSRF 防护（SameSite=Strict Cookie；Portal/Admin 表单接口需 CSRF Token）
- 新增 Backend 内部 API：`/internal/appkey/validate`（供 Edge 调用）

**3.1.2 Edge 网关基础**
- 搭建 Edge 项目骨架（Spring Cloud Gateway 4.x + WebFlux）
- 实现 `TenantContextFilter`（解析路径/IP/时间戳）
- 实现 `AuthGlobalFilter`（HMAC-SHA256 验签 + AppKey Redis 缓存）
- 实现 Edge 路由配置（`/api/open/v1/*` → 本地 forward；`/api/portal/v1/*` + `/api/admin/v1/*` → HTTP 转发 Backend）
- 实现 `BackendClient`（WebClient 调用 Backend）
- 实现 `AppWebFilter`（跨域 CORS）

**3.1.3 Frontend Portal 基础**
- 搭建 Next.js Monorepo（Portal + Admin 双应用 + Turborepo）
- 实现 `packages/api-client`（Axios 实例 + Portal API 接口定义）
- 实现 Portal 登录/注册页面（SSR + TanStack Query）
- 实现 Portal AppKey 管理页面（列表 + 创建 + 禁用/启用 + SecretKey 轮换）
- 实现 Middleware 路由守卫（JWT Cookie 校验）

**3.1.4 端到端联调**
- 注册租户 → 创建 AppKey → HMAC 签名调用 Open API → 验证鉴权通过

**验收标准**：
- [ ] 租户注册成功后数据库 t_tenant + t_user 记录正确
- [ ] AppKey 创建后 secretKey 仅显示一次，AES 加密存储可验证
- [ ] 使用正确 HMAC 签名的 Open API 请求返回 200，签名错误返回 40101
- [ ] Portal 页面可正常登录/注册，AppKey 列表展示正确
- [ ] Refresh Token 可正常刷新 Access Token，过期后返回 401
- [ ] Admin 登录接口（`POST /api/admin/v1/auth/login`）可通过 curl 验证返回 JWT

---

### Phase 2 — 配额与可观测性（6-8 周）

**交付目标**：平台具备流量控制和透明可观测能力

**技术依赖**：Phase 1 完成（租户域 + AppKey 域就绪）

**交付范围**：

**3.2.1 Backend 配额与日志**
- 新增 Flyway 迁移脚本 `V2__init_quota_and_log.sql`（t_quota_plan + t_tenant_quota + t_call_log 按月分区表 + 首月分区）
- 实现分区自动创建机制（pg_cron 定时任务或 Backend 应用层每月自动建下月分区）
- 实现 Backend 配额域（QuotaPlan CRUD + TenantQuota 实例 + Admin 自定义覆盖）
- 实现 Backend 调用日志域（雪花 ID 写入 + 统计聚合 API + CSV 导出 API）
- 实现 Backend WebSocket 服务端（WebFlux WebSocketHandler，Edge 注册 + 心跳 + 批量日志接收）

**3.2.2 Edge 限流、熔断与日志**
- 实现 `RateLimitGlobalFilter`（QPS 令牌桶 Bucket4j + 日/月配额 Redis 计数器）
- 实现 Resilience4j CircuitBreaker（Backend 熔断降级，返回 HTTP 503）
- 实现 `CallLogGlobalFilter`（Sinks.Many bufferTimeout 批量推送 WebSocket 至 Backend）
- 实现 `CallLogService`（本地文件降级 + 后台重传）

**3.2.3 Frontend 可观测性**
- 实现 Portal 调用日志查询页面（时间筛选 + 分页 + 状态码筛选 + CSV 导出）
- 实现 Portal 统计看板（ECharts 折线图 + 饼图 + 接口 Top10）
- 实现 Portal 配额使用量展示（首页仪表盘）

**3.2.4 端到端联调**
- 触发 QPS 限流验证 429 响应
- 调用日志实时可查询（5 秒内）
- 统计图表数据准确

**验收标准**：
- [ ] QPS 超出套餐限制返回 HTTP 429，响应头包含 `X-RateLimit-Limit` + `X-RateLimit-Remaining` + `X-RateLimit-Reset`（毫秒时间戳） + `Retry-After`（秒数）
- [ ] Backend 不可用时，Edge 熔断返回 HTTP 503（Resilience4j 半开状态自动恢复）
- [ ] 单次 API 调用后，日志查询页面 5 秒内可见该条记录
- [ ] 首页展示今日调用量折线图 + 成功率饼图
- [ ] CSV 导出功能正常（最近 30 天数据）

---

### Phase 3 — Webhook 与运营后台（6-8 周）

**交付目标**：平台具备事件驱动通知能力和完整运营管理界面

**技术依赖**：Phase 2 完成（配额域就绪，可触发 Webhook 事件）

**交付范围**：

**3.3.1 Backend Webhook**
- 新增 Flyway 迁移脚本 `V3__init_webhook.sql`（t_webhook_config + t_webhook_event 表）
- 实现 Backend Webhook 域（订阅配置 + DomainEventPublisher + 指数退避重试 + permanent_failed 标记）
- 实现 Backend Webhook Portal API（CRUD + 测试推送 `/webhooks/{id}/test`）
- 实现 Backend 邮件服务（Spring Mail + 阿里云邮件推送）：注册验证邮件 + 配额告警邮件（Phase 2 部分场景提前引入）

**3.3.2 Frontend Portal Webhook**
- 实现 Portal Webhook 管理页面（创建/编辑/删除/测试推送）
- 实现 Portal 测试推送结果展示（HTTP 状态码 + 响应内容）

**3.3.3 Frontend Admin Console**
- 搭建 Next.js Monorepo admin 应用（Monorepo 内独立 package）
- 实现 Admin 租户管理（列表 + 详情 + 暂停/恢复/改套餐）
- 实现 Admin 套餐管理（CRUD + 绑定租户数展示）
- 实现 Admin 系统概览大盘（总租户/活跃租户/今日调用量/成功率）

**3.3.4 端到端联调**
- AppKey 禁用触发 `appkey.disabled` Webhook 事件 → 回调地址收到推送
- Admin 修改租户套餐 → 租户配额实时生效
- 配额耗尽触发 `quota.exceeded` Webhook + 邮件通知

**验收标准**：
- [ ] Webhook 事件触发后，回调地址 10 秒内收到 POST 请求（含 HMAC 签名）
- [ ] 推送失败自动指数退避重试（1min → 5min → 30min → 2h → 6h），5 次后标记 permanent_failed
- [ ] Admin 控制台可查看所有租户列表和详情
- [ ] Admin 可暂停/恢复租户，租户状态变更即时生效

---

### Phase 4 — AI 大脑（10-12 周）

**交付目标**：租户可配置 AI 大脑，Edge 大脑组装执行端到端 AI 请求

**技术依赖**：Phase 2-3 完成（Phase 2 提供 WebSocket 通道基础；Phase 3 提供 Admin Console 用于配置下发监控）

**交付范围**：

**3.4.1 Backend AI 配置域**
- 新增 Flyway 迁移脚本 `V4__init_ai_config.sql`（t_rag_config / t_rag_document / t_agent_config / t_workflow_config / t_workflow_node / t_tenant_brain_config 表）
- 实现 Backend RAG 配置域（知识库 CRUD + 文档上传 + PGvector 向量化 + 状态机 pending/processing/completed/failed）
- 实现 Backend Agent 配置域（角色 + 提示词 + 模型 + 工具 + TTS/ASR JSON 配置）
- 实现 Backend Workflow 配置域（流程 CRUD + 节点类型定义 + 乐观锁版本）
- 实现 Backend 配置下发域（TenantBrainConfig JSON 快照组装 + WebSocket 推送 + ACK 处理）
- 新增 Backend Admin API：`POST /brain-configs/{tenantId}/dispatch`（异步下发）

**3.4.2 Edge 大脑组装**
- 实现 `BrainAssemblyService`（ToolRegistry + L1 Caffeine/L2 Redis 多级缓存 + stale-while-revalidate 降级）
- 实现 `EdgeWebSocketClient`（Reactor Netty WebSocket + 重连指数退避）
- 实现 `ConfigMessageHandler`（CONFIG_UPDATE 消息处理 + ACK 发送）
- 实现 `BrainContext` 上下文组装（RAG 检索器 + AIAgent + WorkflowEngine + DashScopeTtsClient + DashScopeAsrClient）

**3.4.3 Edge Open API 业务接口**
- 实现 `/api/open/v1/ai/chat`（Agent + RAG，流式/非流式）
- 实现 `/api/open/v1/ai/tts`（DashScope TTS）
- 实现 `/api/open/v1/ai/asr`（DashScope ASR）
- 实现 `/api/open/v1/ai/workflow`（Workflow 引擎执行）
- 实现 `/api/open/v1/ai/agent`（完整 Agent 交互，文本/语音多模式）
- 实现会话存储（Redis L2 + Caffeine L1，滑动窗口上下文）

**3.4.4 Frontend AI 配置界面**
- 实现 Portal RAG 管理页面（知识库 CRUD + 文档上传 + 向量化状态 + 检索测试）
- 实现 Portal Agent 配置页面（Monaco Editor 提示词 + TTS/ASR 滑块 + 工具多选）
- 实现 Portal Workflow 编排画布（ReactFlow 拖拽 + 节点配置抽屉 + 版本发布）
- 实现 Portal Workflow 版本管理（历史版本 + 回滚）
- 实现 Admin 大脑配置下发状态监控（pending/dispatched/failed 状态 + 重下发 + requestId 查日志）

**3.4.5 端到端联调**
- RAG 知识库配置 → 手动触发下发 → Edge 缓存更新 → /ai/chat 带 RAG 增强回答
- Agent TTS 配置修改 → 下发 → /ai/tts 使用新音色
- Workflow 发布 → 下发 → /ai/workflow 完整执行

**验收标准**：
- [ ] RAG 知识库配置保存后，Admin 可手动触发下发，Edge 收到 CONFIG_UPDATE 消息并返回 ACK
- [ ] `/ai/chat` 请求带 RAG 检索增强时，返回引用了知识库内容的回答
- [ ] `/ai/tts` 返回真实音频 URL（非 mock）
- [ ] `/ai/workflow` 按节点顺序执行，条件分支按 SpEL 表达式正确分流
- [ ] 所有 Edge 实例断开时，新请求返回 `BRAIN_NOT_READY`（HTTP 503）

---

### Phase 5 — 完善与优化（4-6 周）

**交付目标**：平台达到生产就绪状态

**技术依赖**：Phase 4 完成（全量功能就绪）

**交付范围**：

**3.5.1 运营功能完善**
- 实现 SecretKey 轮换提前提醒邮件
- 实现套餐管理可视化（绑定租户数图表）

**3.5.2 Workflow 高级能力**
- 实现循环节点（while / for 配置）
- 实现并行分支（parallel gateway）
- 实现子流程调用（sub-workflow）

**3.5.3 Agent 多模态增强**
- 实现图片输入（多模态 LLM，支持图片作为上下文）
- 实现语音流式输入（ASR 实时流式上传）

**3.5.4 性能优化**
- 性能压测（目标：Edge 鉴权 P99 ≤ 5ms；非 AI Open API P99 ≤ 200ms；AI chat TTFB ≤ 2s）
- Backend 连接池调优（R2DBC pool max-size 100 → 实测最优值）
- Edge L1 Caffeine 命中率优化（基于访问热力图调整 TTL）
- Next.js 编译优化（Ant Design 按需加载 + CDN 静态资源）

**3.5.5 可观测性**
- Prometheus + Grafana 接入（调用量 + 错误率 + 延迟分位数）
- Grafana 预置看板（平台运营大盘 + AI 能力监控）

**3.5.6 数据库优化**
- 新增 Flyway 迁移脚本 `V5__init_call_log_agg.sql`（t_call_log_daily_agg 预聚合表，加速 30 天以上查询）

**验收标准**：
- [ ] Edge 鉴权 P99 实测 ≤ 5ms（压力测试 1000 QPS）
- [ ] `/ai/chat` 流式响应 TTFB 实测 ≤ 2s（DashScope Qwen 响应正常）
- [ ] Prometheus 可见全部核心指标（Gauge + Counter + Histogram）
- [ ] SecretKey 轮换提醒邮件发送成功

---

## 4. 迭代依赖图

```
Phase 1 ──► Phase 2 ──► Phase 3 ──► Phase 4 ──► Phase 5
 (8-10周)     (6-8周)     (6-8周)     (10-12周)    (4-6周)
                │                        ▲
                └────── WebSocket 通道 ───┘
```

> **团队规模假设**：以上工期按 **1-2 名全栈开发** 估算。若 3 人以上团队，Phase 1-3 可并行前后端（Backend + Frontend 各 1 人），总工期可压缩约 30%。Phase 4 是复杂度最高的阶段（RAG + Agent + Workflow + TTS/ASR + ReactFlow），建议预留 2 周风险缓冲。

---

## 5. 数据库迁移版本规划

| 脚本 | 内容 | 所属 Phase |
|------|------|-----------|
| `V1__init_tenant.sql` | t_user + t_tenant + t_app_key（含 SecretKey 轮换字段） | Phase 1 |
| `V2__init_quota_and_log.sql` | t_quota_plan + t_tenant_quota + t_call_log（按月分区主表 + 首月分区） | Phase 2 |
| `V3__init_webhook.sql` | t_webhook_config + t_webhook_event | Phase 3 |
| `V4__init_ai_config.sql` | t_rag_config + t_rag_document + t_agent_config + t_workflow_config + t_workflow_node + t_tenant_brain_config | Phase 4 |
| `V5__init_call_log_agg.sql` | t_call_log_daily_agg（调用日志预聚合表，加速 30 天以上查询） | Phase 5 |

---

## 6. 技术栈汇总

| 组件 | 技术 | 版本 |
|------|------|------|
| Backend 框架 | Spring Boot | 4.0.6 |
| Backend Web | Spring WebFlux | 6.x |
| Backend ORM | Spring Data R2DBC | 3.x |
| Backend 数据库 | PostgreSQL | 16.x |
| Backend 向量 | PGvector | - |
| Backend AI | Spring AI + DashScope | 2.0.0-M4 |
| Edge 框架 | Spring Cloud Gateway | 4.x |
| Edge 限流 | Bucket4j + Redis | 8.x / 7.x |
| Edge 缓存 | Caffeine | - |
| Edge 熔断 | Resilience4j | 2.x |
| Frontend 框架 | Next.js | 15.x |
| Frontend UI | Ant Design | 5.x |
| Frontend 状态 | Zustand + TanStack Query | 4.x / 5.x |
| 构建工具 | Turborepo | - |

---

## 7. 关键设计决策记录

| 决策 | 选择 | 理由 |
|------|------|------|
| ID 生成策略 | 业务表 BIGINT 自增序列，调用日志表雪花算法 | 自增序列简化实现，雪花算法避免高并发写热点 |
| 配置粒度 | t_tenant_brain_config 以租户维度整合（JSON 快照） | 下发简单，Edge 只需一次缓存查找；当前规模足够 |
| 会话存储 | Redis L2 + Caffeine L1，Edge 不持久化 | 跨实例共享 + 热缓存，Edge 无状态可快速弹性扩缩 |
| WebSocket 方向 | Edge 主动连接 Backend（拉模式） | Edge 为网关入口，Gateway 无法被 Backend 主动连接 |
| SpEL 安全 | SimpleEvaluationContext + 白名单校验 | 图灵完备表达式需严格沙箱，禁止 T() / Runtime 调用 |
