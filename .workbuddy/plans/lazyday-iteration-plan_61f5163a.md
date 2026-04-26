---
name: lazyday-iteration-plan
overview: 从 5 份架构/需求文档中提取全部功能模块，按依赖关系和交付价值划分 5 个迭代阶段，并输出迭代计划文档（迭代名称、目标、交付范围、技术依赖、验收标准）。
todos:
  - id: phase-1
    content: |-
      **Phase 1 — 基础开放平台（8-10 周）**
      交付目标：开发者可注册租户、创建 AppKey、通过 Open API 验证身份并调用基础管理接口

      - `[subagent:code-explorer]` 探索 backend 和 edge 项目现有结构，确认为 Phase 1 需要新建和修改的模块
      - 搭建项目基础（Flyway 迁移 V1、DDD 包结构、通用响应格式、错误码）
      - 实现 Backend 租户域（t_tenant / t_user）和 AppKey 域（t_app_key + SecretKey AES 加密）
      - 实现 Backend 鉴权接口（JWT：登录/注册/Refresh Token）
      - 实现 Backend Portal API（租户注册 + AppKey CRUD + SecretKey 轮换宽限期）
      - 实现 Backend Admin API（租户列表 + 暂停/恢复）
      - 实现 Edge 网关基础（TenantContextFilter + AuthGlobalFilter：HMAC 验签 + AppKey 缓存 Redis）
      - 实现 Edge 路由配置（/api/open/v1/* → 本地 forward；/api/portal/v1/* 和 /api/admin/v1/* → HTTP 转发 Backend）
      - 实现 Frontend Portal 基础（登录/注册 + AppKey 管理页面，SSR + TanStack Query）
      - 端到端联调（注册 → 创建 AppKey → 调用 Open API）
    status: completed
  - id: phase-2
    content: |-
      **Phase 2 — 配额与可观测性（6-8 周）**
      交付目标：平台具备流量控制和透明可观测能力

      - 新增 Flyway 迁移 V2（t_quota_plan）
      - 实现 Backend 配额域（QuotaPlan CRUD + 租户绑定 + 套餐动态修改实时生效）
      - 实现 Edge 限流过滤器（RateLimitGlobalFilter：QPS 令牌桶 + 日/月配额 Redis 计数器）
      - 实现 Edge 调用日志过滤器（CallLogGlobalFilter：Sinks.Many buffer 批量推送 WebSocket 至 Backend）
      - 实现 Backend 调用日志域（t_call_log 按月分区 + 统计聚合 API）
      - 实现 Backend WebSocket 服务端（Edge 注册、心跳、批量日志接收）
      - 实现 Frontend Portal 日志查询页面（时间筛选 + 分页 + 导出 CSV）
      - 实现 Frontend Portal 统计看板（ECharts 折线图 + 饼图）
      - 端到端联调（限流生效 + 日志查询 + 统计聚合）
    status: completed
    dependencies:
      - phase-1
  - id: phase-3
    content: |-
      **Phase 3 — Webhook 与运营后台（6-8 周）**
      交付目标：平台具备事件驱动能力和完整运营管理界面

      - 新增 Flyway 迁移 V3（t_webhook_config / t_webhook_event）
      - 实现 Backend Webhook 域（订阅配置 + DomainEventPublisher + 指数退避重试 + 永久失败标记）
      - 实现 Backend Webhook Portal API（CRUD + 测试推送）
      - 实现 Frontend Portal Webhook 管理页面（创建/编辑/测试）
      - 实现 Frontend Admin Console（Next.js Monorepo admin 应用搭建）
      - 实现 Admin 租户管理（列表 + 详情 + 暂停/恢复/改套餐）
      - 实现 Admin 套餐管理（CRUD + 绑定租户数展示）
      - 实现 Admin 系统概览大盘（总租户/活跃租户/调用量/成功率）
      - 端到端联调（Webhook 事件触发 + 推送成功/重试 + 告警邮件）
    status: completed
    dependencies:
      - phase-2
  - id: phase-4
    content: |-
      **Phase 4 — AI 大脑（10-12 周）**
      交付目标：租户可配置 AI 大脑，Edge 大脑组装执行端到端 AI 请求

      - 新增 Flyway 迁移 V4（t_rag_config / t_rag_document / t_agent_config / t_workflow_config / t_workflow_node / t_tenant_brain_config）
      - 实现 Backend RAG 配置域（知识库 CRUD + 文档上传 + PGvector 向量化 + 状态机 pending/processing/completed/failed）
      - 实现 Backend Agent 配置域（角色 + 提示词 + 模型 + 工具 + TTS/ASR JSON 配置）
      - 实现 Backend Workflow 配置域（流程 CRUD + 节点类型定义 + 乐观锁版本）
      - 实现 Backend 配置下发域（TenantBrainConfig JSON 快照组装 + WebSocket 推送）
      - 实现 Edge 大脑组装服务（BrainAssemblyService：ToolRegistry + L1 Caffeine/L2 Redis 多级缓存 + 缓存降级 stale-while-revalidate）
      - 实现 Edge 大脑执行器（RAG 检索器 + AIAgent + WorkflowEngine + DashScopeTtsClient + DashScopeAsrClient）
      - 实现 Frontend RAG 管理页面（文档上传 + 向量化状态 + 检索测试 + 知识库详情）
      - 实现 Frontend Agent 配置页面（Monaco Editor 提示词 + TTS/ASR 滑块 + 工具多选）
      - 实现 Frontend Workflow 编排画布（ReactFlow 拖拽 + 节点配置抽屉 + 版本发布）
      - 实现 Frontend Admin 大脑配置下发状态监控（pending/dispatched/failed + 重下发 + requestId 查日志）
      - 实现 Edge Open API 业务接口（/ai/chat + /ai/tts + /ai/asr + /ai/workflow + /ai/agent）
      - 端到端联调（配置 → 下发 → Edge 执行 → 响应），包含会话存储 Redis L2 缓存
    status: completed
    dependencies:
      - phase-3
  - id: phase-5
    content: |-
      **Phase 5 — 完善与优化（4-6 周）**
      交付目标：平台达到生产就绪状态

      - 实现 Backend 邮件服务（Spring Mail + 阿里云邮件推送：注册验证 + 配额告警 + SecretKey 轮换提醒）
      - 实现 Workflow 高级节点（循环/并行/子流程）
      - 实现 Agent 多模态支持（图片输入 + 语音流式输入）
      - 性能压测与调优（目标：Edge 鉴权 P99 ≤ 5ms；非 AI Open API P99 ≤ 200ms；AI chat TTFB ≤ 2s）
      - Prometheus + Grafana 可观测性接入（调用量 + 错误率 + 延迟分位数）
      - CDN 静态资源优化（Next.js 编译输出 + Ant Design 按需加载）
    status: completed
    dependencies:
      - phase-4
---

## 产品概述

Lazyday 是一个多租户 API 开放平台，采集中枢（Frontend + Backend）、大脑（Edge）两层架构。平台向外部开发者提供标准化 Open API（AI 对话 / TTS / ASR / Workflow / Agent），同时提供 Portal 自助控制台和 Admin 运营后台。

## 核心功能模块（从 5 份文档提取）

**基础设施层**

- 统一鉴权（HMAC 签名 + JWT）
- 限流/配额（QPS + 日/月配额）
- 调用日志（按月分区 + 统计聚合）
- WebSocket 通道（配置下发 + 日志推送）
- 会话存储（L1 Caffeine / L2 Redis）

**租户与接入层**

- 租户注册/登录（无邮箱验证）
- AppKey 管理（CRUD + SecretKey 轮换 + 宽限期）
- 套餐/配额管理

**AI 能力配置层（中枢）**

- RAG 知识库（CRUD + 文档上传/向量化 + 检索参数）
- Agent 配置（角色 + 提示词 + 模型参数 + 工具绑定 + TTS/ASR）
- Workflow 编排（CRUD + 可视化画布 + 版本管理 + 条件分支）
- 配置下发（租户维度 JSON 快照 + WebSocket 推送）

**AI 能力执行层（大脑）**

- RAG 检索器（PGvector）
- Agent 执行器（Spring AI + DashScope Qwen）
- Workflow 引擎（节点执行 + 递归子大脑）
- TTS 语音合成
- ASR 语音识别
- 工具注册表（ToolRegistry）

**管理运营层**

- Portal 控制台（AppKey / 日志 / 统计看板）
- Admin 运营后台（租户 + 套餐 + 系统概览）
- Webhook 事件订阅与推送

## 功能优先级矩阵

| 优先级 | 功能 | 价值 | 依赖 | 说明 |
| --- | --- | --- | --- | --- |
| P0 | 租户注册 + 登录 | 平台入口 | 无 | 无邮箱验证直接激活 |
| P0 | AppKey 管理（CRUD） | API 接入前提 | 租户域 | SecretKey AES 加密存储 |
| P0 | Edge 网关基础（路由 + 鉴权） | 所有流量的必经之路 | 无 | 阻塞所有请求 |
| P1 | Open API 基础调用（非 AI） | 平台核心价值 | AppKey + 鉴权 | 管理类 API 走 HTTP 转发 |
| P1 | 调用日志（采集 + 明细查询） | 可观测性基础 | Edge 鉴权 | 按月分区，WebSocket 推送 |
| P1 | 限流/配额（QPS + 日/月） | 商业化前提 | AppKey + 配额域 | Redis 令牌桶 |
| P2 | Portal 控制台首页 + 统计图表 | 开发者留存 | 调用日志统计 API | 折线图 + 饼图 |
| P2 | 套餐管理（Admin） | 商业化基础 | 配额域 | Free/Pro/Enterprise 模板 |
| P2 | 租户管理（Admin） | 平台运营基础 | 租户域 | 暂停/恢复/改套餐 |
| P2 | Webhook（配置 + 推送 + 重试） | 事件驱动 | AppKey + 配额域 | 指数退避重试 |
| P3 | AI Chat（非 RAG） | AI 能力验证 | 大脑组装 + Agent 配置域 | 最简 LLM 调用 |
| P3 | RAG 知识库管理 + 向量化 | 知识增强基础 | Agent 配置域 | PGvector 检索 |
| P3 | AI Chat + RAG 增强 | 核心差异化能力 | RAG 检索 + Agent | 端到端验证 |
| P3 | 配置下发（WebSocket） | 中枢-大脑协同 | RAG/Agent/Workflow 配置域 | 租户维度 JSON 快照 |
| P4 | TTS / ASR 配置 + 执行 | 多模态语音能力 | Agent 配置域 | DashScope TTS/ASR |
| P4 | Workflow 可视化编排 | 复杂流程编排 | 配置下发 + Agent | ReactFlow 画布 |
| P4 | Agent 完整交互（文本/语音） | 多模态 Agent | TTS/ASR + Workflow | ASR → Agent → TTS |
| P5 | 邮件服务（注册验证/告警） | 运营成熟度 | 租户域 + Webhook | Phase 2 引入 |
| P5 | Workflow 高级节点（循环/并行/子流程） | 复杂场景 | Workflow 基础 | Phase 5 |
| P5 | 性能压测与调优 | 规模化准备 | Phase 4 完成 | 最终验收 |


## 设计约束

- 数据库 ID 策略：业务表 BIGINT 自增序列，调用日志 BIGINT 雪花算法
- Edge 为大脑执行层，不持久化配置；Backend 为中枢核心服务，不执行 AI 推理
- 全异步响应式架构（Spring WebFlux），网关用 Spring Cloud Gateway 4.x
- Frontend 为 Next.js 15 SSR，分为 Portal（租户控制台）和 Admin（运营后台）
- 工具注册在 Edge 本地（ToolRegistry），配置中只存工具名称列表

## 技术架构

### 系统分层

```
Phase 1-3: 中枢 Backend（Spring Boot 4 + WebFlux）
Phase 4-5: 大脑 Edge（Spring Cloud Gateway + WebFlux）
Phase 1-5: Frontend（Next.js 15 + Ant Design + Monorepo）
```

### 数据流

- **请求流**：客户端 → Edge（鉴权 + 限流）→ Backend（管理类）或 Edge 大脑（AI 类）
- **配置流**：Frontend → Backend（保存）→ WebSocket → Edge 大脑（组装执行）
- **日志流**：Edge → WebSocket → Backend（批量写库）

### 关键依赖

- PostgreSQL 16 + PGvector（RAG 向量存储）
- Redis 7（限流 / 缓存 / 会话）
- DashScope API（LLM / TTS / ASR / Embedding）

### 数据库迁移策略

Flyway 按 Phase 分版本迁移：

- `V1__init_tenant.sql` — Phase 1
- `V2__init_quota.sql` — Phase 2
- `V3__init_webhook.sql` — Phase 3
- `V4__init_ai_config.sql` — Phase 4
- `V5__init_session.sql` — Phase 5

## 迭代划分原则

1. **每个迭代端到端可运行**：前端 + 后端 + 网关，闭环一个用户场景
2. **依赖关系先行**：网关鉴权 → 限流 → 管理 API → AI 执行
3. **AI 能力后置**：AI 能力依赖 Phase 1-2 的网关和管理基础设施
4. **大脑独立交付**：Edge 大脑组装在 Phase 4 一次交付完整 AI 能力栈