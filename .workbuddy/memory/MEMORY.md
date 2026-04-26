# lazyday 项目长期记忆

## 项目定位
多租户 API 开放平台（Open Platform），包含三层：Backend + Edge + Frontend。

## 技术栈

### Backend（已实现）
- Spring Boot 4.0.6 + WebFlux + R2DBC + PostgreSQL
- DDD 分层架构：interfaces → application → domain → infrastructure
- API 路由：@RequestMappingApiV1(/api/lazyday/v1)、@RequestMappingApiV2(/api/lazyday/v2)、@RequestMappingOpenV1(/api/open/v1)
- Spring AI 2.0.0-M4 + DashScope(Qwen) + PGvector
- 数据库迁移：Flyway 多方言支持
- 加密：自实现 AES/RSA/HMAC
- ID：业务表自增序列 / 高并发日志表雪花算法

### Edge（规划中）
- Spring Cloud Gateway 4.x + WebFlux
- 鉴权：HMAC-SHA256 签名（X-App-Key + X-Timestamp + X-Nonce + X-Sign）
- 限流：Bucket4j + Redis（令牌桶）
- 熔断：Resilience4j
- **大脑组装**：RAG 检索器 + Agent 执行器 + Workflow 引擎 + TTS/ASR 客户端
- **配置同步**：WebSocket 接收 Backend 下发的 RAG/Agent/Workflow 配置

### Frontend（已搭建骨架）
- Next.js 15.5.15 + TypeScript 5.9.3 + Ant Design 5.x
- Monorepo：apps/portal（:3001）+ apps/admin（:3002）
- 构建：Turborepo 2.9.6 + pnpm 10.33.2
- 共享 packages：types / utils / api-client / ui
- 状态：Zustand 5.x + TanStack Query 5.x
- 认证：JWT + HTTP-Only Cookie + Middleware 路由守卫
- API 层：Axios 双实例（portalClient / adminClient）+ 全量接口定义
- 路由：Portal 15 个路由 / Admin 12 个路由（含 AI 功能占位页面）
- **流程编排**：ReactFlow（Workflow 可视化画布，Phase 4 实现）

## 功能范围（标准版 + AI 大脑）
- 租户管理 + AppKey/Secret 鉴权 + 限流/配额 + 调用日志 + Webhook
- **RAG 知识库管理**：文档上传、向量化、检索配置
- **Agent 配置管理**：角色定义、提示词、工具绑定、TTS/ASR 配置
- **Workflow 流程编排**：可视化画布、节点编辑、版本管理
- **配置下发**：Backend 组装 RAG/Agent/Workflow 配置，通过 WebSocket 推送至 Edge
- **大脑组装**：Edge 接收配置后组装 AI 大脑，处理 Open API 请求（chat / tts / asr / workflow / agent）

## 文档位置
- docs/architecture-overview.md — 整体架构
- docs/backend-architecture.md — 后端架构
- docs/edge-architecture.md — Edge 网关架构
- docs/frontend-architecture.md — 前端架构
- docs/requirements-design.md — 需求设计
- docs/iteration-plan.md — 迭代计划（v0.2，5 个 Phase）

## 数据库规范
- 表前缀：t_
- 主键：BIGINT 自增序列（业务表）/ 雪花算法（高并发日志表）
- 软删除：deleted 字段（0=正常，1=删除）
- 调用日志表 t_call_log 按月分区

## API 路由规划
- /api/lazyday/v1、/api/lazyday/v2 — 内部 API
- /api/open/v1 — 开放 API（经 Edge 鉴权）
- /api/admin/v1 — 管理后台（待新增注解）
- /api/portal/v1 — 开发者控制台（待新增注解）

## 项目负责人
fanqibu（作者：bufanqi/chenbin/fan 等为同一人历史写法）
