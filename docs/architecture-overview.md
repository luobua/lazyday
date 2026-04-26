# Lazyday 开放平台 — 整体架构设计

> 版本：v0.5
> 日期：2026-04-25
> 作者：fanqibu

---

## 修订记录

| 版本 | 日期 | 修订内容 |
|------|------|---------|
| v0.5 | 2026-04-25 | ID 策略统一为 BIGINT 自增序列（移除 UUID）；技术选型表 ID 生成描述更新；§3.1 表格格式修正 |
| v0.4 | 2026-04-25 | **M-1**：架构图说明补充 Frontend 定位——归属于中枢体系的可视化管控界面，独立部署但归属中枢体系；全文贯穿「中枢（Frontend+Backend）」与「大脑（Edge）」概念分层 |
| v0.3 | 2026-04-25 | 引入多租户、Edge、Admin、配置下发等完整规划 |
| v0.2 | 2026-04-25 | 补充鉴权流程、部署架构 |
| v0.1 | 2026-04-25 | 初始版本 |

---

---

## 1. 平台定位

Lazyday 是一个**多租户开放接口平台**，向外部开发者/企业租户提供标准化 API 接入能力，同时提供：

- **开发者控制台（Developer Portal）**：租户自助管理 AppKey、查看 API 文档、监控调用统计
- **运营管理后台（Admin Console）**：平台运营人员管理租户、配额、账单、系统配置

---

## 2. 系统整体架构图

```
┌──────────────────────────────────────────────────────────────────────┐
│                          客户端层 (Clients)                          │
│                                                                      │
│   ┌──────────────────────┐      ┌──────────────────────────────┐    │
│   │  开发者控制台          │      │  外部 API 调用方              │    │
│   │  Developer Portal    │      │  (第三方应用 / SDK / CLI)    │    │
│   │  (Next.js SSR)       │      │                              │    │
│   │                      │      │                              │    │
│   │  管理后台             │      │                              │    │
│   │  Admin Console       │      │                              │    │
│   │  (Next.js SSR)       │      │                              │    │
│   └──────────┬───────────┘      └──────────────┬───────────────┘    │
└──────────────┼──────────────────────────────────┼────────────────────┘
               │ HTTPS                            │ HTTPS
               ▼                                  ▼
╔══════════════════════════════════════════════════════════════════════╗
║                          中枢层 (Central)                             ║
║                                                                      ║
║  ┌──────────────────────────────┐  ┌─────────────────────────────┐ ║
║  │      Frontend — 管理界面       │  │     Backend — 核心服务        │ ║
║  │   Next.js SSR / Ant Design   │  │  Spring Boot + WebFlux      │ ║
║  │                              │  │  DDD 分层架构                │ ║
║  │  · 租户/用户/套餐管理          │  │                             │ ║
║  │  · RAG 知识库管理界面          │  │  · 租户域 / AppKey 域        │ ║
║  │  · Agent 配置界面（含 TTS/ASR）│  │  · 配额域 / 调用日志域        │ ║
║  │  · Workflow 可视化编排         │  │  · Webhook 域                │ ║
║  │  · 配置下发状态监控            │  │  · RAG / Agent / Workflow   │ ║
║  │  · 调用统计看板                │  │    配置域                    │ ║
║  │                              │  │  · 配置下发域（WebSocket 推送）│ ║
║  └──────────────┬───────────────┘  └──────────────┬──────────────┘ ║
╚══════════════════┼════════════════════════════════┼═════════════════╝
                    │ WebSocket（配置下发/日志推送）    │
                    │ HTTP / gRPC（服务调用）          │
                    ▼                                  ▼
┌──────────────────────────────────────────────────────────────────────┐
│                       Edge 层 — 大脑 (Brain)                          │
│                                                                      │
│   ┌──────────────────────────────────────────────────────────────┐   │
│   │                    Edge Service                              │   │
│   │              Spring Cloud Gateway / WebFlux                  │   │
│   │                                                              │   │
│   │  ┌────────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │   │
│   │  │ 路由分发    │  │ 租户鉴权  │  │ 限流/配额 │  │ 日志采集  │ │   │
│   │  │ Routing    │  │ Auth     │  │ RateLimit│  │ Logging  │ │   │
│   │  └────────────┘  └──────────┘  └──────────┘  └──────────┘ │   │
│   │  ┌──────────────────────────────────────────────────────┐   │   │
│   │  │              🔥 大脑组装 (Brain Assembly)             │   │   │
│   │  │   接收中枢下发的 RAG / Agent / Workflow 配置         │   │   │
│   │  │   动态组装：RAG检索 + Agent推理 + Workflow编排        │   │   │
│   │  │            + TTS语音合成 + ASR语音识别               │   │   │
│   │  └──────────────────────────────────────────────────────┘   │   │
│   └──────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                       基础设施层 (Infrastructure)                     │
│                                                                      │
│   ┌──────────────┐  ┌───────────────┐  ┌───────────┐               │
│   │  PostgreSQL  │  │     Redis      │  │  PGvector │               │
│   │  (主数据库)   │  │  (缓存/限流)   │  │  (向量库)  │               │
│   └──────────────┘  └───────────────┘  └───────────┘               │
└──────────────────────────────────────────────────────────────────────┘
```

> **中枢与大脑的分工**：
> - **中枢（Frontend + Backend）**：负责 AI 能力的**管理、配置、监控和调度**。不直接执行 AI 逻辑，而是将配置好的大脑蓝图下发给 Edge。
> - **大脑（Edge）**：负责 AI 能力的**执行**。接收中枢的配置后，组装 RAG 检索器、Agent 推理机、Workflow 引擎、TTS/ASR 引擎，处理实际的 AI 请求。
>
> **Frontend 的定位说明**：Developer Portal 和 Admin Console 以独立客户端形式部署（Next.js SSR），是中枢的可视化管控**界面**，不执行 AI 推理，通过 Backend API 管理中枢配置。图中将其置于「中枢层」表示其归属于中枢体系，而非将其归为普通客户端。

---

## 3. 核心模块说明

### 3.1 中枢层 — Frontend（管理界面）

Frontend 是**中枢的可视化管控台**，面向运营人员和租户开发者，提供 AI 能力的配置与管理入口：

| 应用 | 用途 | 定位 |
|------|------|------|
| Developer Portal | 开发者/租户自助控制台 | 中枢 — 租户级 AI 配置管理 |
| Admin Console | 平台运营管理后台 | 中枢 — 平台级 AI 监管与调度 |

**管理功能**：
- **RAG 管理**：知识库创建、文档上传、向量化配置、检索参数调优
- **Agent 管理**：AI Agent 角色定义、系统提示词、工具绑定、TTS/ASR 引擎配置
- **Workflow 管理**：可视化流程编排、节点配置、执行策略、版本管理
- **配置下发**：RAG/Agent/Workflow 配置通过 WebSocket 实时推送至 Edge 大脑
- **大脑状态监控**：查看配置下发状态、Edge 大脑健康状态

### 3.2 中枢层 — Backend（核心服务）

Backend 是**中枢的核心服务层**，负责业务逻辑、配置管理、配置下发，不直接执行 AI 推理：

| 业务域 | 职责 |
|--------|------|
| 用户域 (User) | 账号注册/登录/管理 |
| 租户域 (Tenant) | 租户注册、套餐绑定、状态管理 |
| AppKey 域 | AppKey/Secret 的生成、轮换、禁用 |
| 配额域 (Quota) | 调用次数、QPS 限制配置 |
| 调用日志域 (CallLog) | 接口调用记录、统计聚合 |
| Webhook 域 | 事件订阅、回调地址管理、推送重试 |
| **RAG 配置域** | **知识库配置、文档向量化、检索策略管理** |
| **Agent 配置域** | **AI Agent 角色定义、系统提示词、工具绑定、TTS/ASR 引擎配置** |
| **Workflow 配置域** | **流程编排、节点定义、执行策略、版本管理** |
| **配置下发域** | **将 RAG/Agent/Workflow 配置组装后通过 WebSocket 推送至 Edge 大脑** |
| Open API 域 | 对外暴露的业务接口（/api/open/v1/*） |

> **中枢通信**：Backend 与 Edge 之间采用 **WebSocket 双向通信**，支持：
> - 配置下发（RAG/Agent/Workflow 配置从 Backend 推送至 Edge 大脑）
> - 调用日志流（Edge 将调用日志实时推送至 Backend）
> - Webhook 事件通知
> - 兼容 HTTP 请求-响应模式

### 3.3 Edge 层 — 大脑（AI 执行单元）

Edge 是**大脑的执行层**，基于 **Spring Cloud Gateway + WebFlux**，承担 AI 推理任务的实际执行：

- **路由分发**：将请求按租户、路径转发到 Backend（Open API 走大脑执行路径）
- **租户鉴权**：解析 `X-App-Key` / `Authorization` Header，验证 AppKey+Secret 签名
- **限流/配额**：基于 Redis 令牌桶，按租户/接口维度控制 QPS 和月调用量
- **日志采集**：记录每次请求的调用链、响应时间、状态码，写入调用日志
- **熔断降级**：集成 Resilience4j，防止后端雪崩
- **🔥 大脑组装**：接收中枢下发的 RAG/Agent/Workflow 配置，动态组装 AI 大脑，处理 Open API 请求（chat / tts / asr / workflow / agent）

> **大脑职责**：大脑**不存储配置**，只执行配置。中枢（Backend）通过 WebSocket 将配置下发至 Edge，Edge 在内存/Caffeine 缓存中组装 BrainContext，处理请求后**不持久化任何 AI 状态**。

### 3.4 基础设施层

| 组件 | 用途 |
|------|------|
| PostgreSQL | 主业务数据存储（已有） |
| Redis | 限流令牌桶、AppKey 缓存、Session |
| PGvector | AI 语义检索（已有） |

---

## 4. API 路由规划

```
/api/lazyday/v1/*    → 内部 API（Backend 自用，需登录态）
/api/lazyday/v2/*    → 内部 API v2（Backend 自用，需登录态）
/api/open/v1/*       → 开放 API（经 Edge 鉴权，供第三方调用，走 Edge 大脑执行）
/api/admin/v1/*      → 管理后台 API（需 Admin 权限，Backend 面向 Admin Console 的 API）
/api/portal/v1/*     → 开发者控制台 API（需租户登录态，Backend 面向 Portal 的 API）
```

---

## 5. 鉴权流程

### 5.1 开放 API 鉴权（第三方调用）

```
第三方应用                    Edge 大脑                        中枢 Backend
    │                             │                               │
    │── POST /api/open/v1/xxx ───►│                               │
    │   Header: X-App-Key: xxx    │                               │
    │   Header: X-Timestamp: xxx   │                               │
    │   Header: X-Sign: HMAC-SHA256│                              │
    │                             │── 查缓存/DB 验证 ──────────────►│
    │                             │◄── 租户信息 + 配额 ─────────────│
    │                             │                               │
    │                             │── 限流检查（Redis）            │
    │                             │                               │
    │                             │── 大脑组装（加载配置）          │
    │                             │   执行业务逻辑 ─────────────── │
    │                             │   （RAG / Agent / Workflow）   │
    │◄── 响应 ────────────────────│                               │
    │                             │── 推送调用日志至中枢 ─────────►│
```

> **注意**：Open API 请求（如 `/api/open/v1/ai/chat`）**不转发至 Backend**，由 Edge 大脑直接执行。其他管理类 Open API（需鉴权但非 AI 逻辑）走 HTTP 转发。

### 5.2 控制台鉴权（租户/Admin 登录）

基于 JWT Token，登录后颁发 Access Token + Refresh Token，存入 HTTP-Only Cookie。

---

## 6. 部署架构（目标态）

```
Internet
    │
    ▼
[Nginx / CDN]
    │
    ├──► [Next.js Frontend SSR]  (:3000)   Portal + Admin (Node.js 运行)
    │       │
    │       └──► 静态资源 → CDN
    │
    └──► [Edge Service — 大脑]  (:8080)
              │
              │ WebSocket（配置下发/日志推送）
              │
              └──► [Backend Service — 中枢核心]  (:8081)
                        │
                        ├──► [PostgreSQL]  (:5432)
                        ├──► [Redis]       (:6379)
                        └──► [PGvector]    (:5432)
```

---

## 7. 技术选型汇总

| 层次 | 技术 | 版本 | 说明 |
|------|------|------|------|
| Frontend | Next.js + TypeScript | 15.x | App Router, SSR/SSG/CSR |
| Frontend UI | Ant Design | 5.x |
| Edge | Spring Cloud Gateway | 4.x | 大脑执行层 |
| Edge 限流 | Redis + Lua / Bucket4j | - |
| Backend | Spring Boot + WebFlux | 4.0.6 | 中枢核心服务 |
| Backend ORM | Spring Data R2DBC | 3.x |
| Database | PostgreSQL | 16.x |
| Cache | Redis | 7.x |
| AI | Spring AI + DashScope | 2.0.0-M4 | 阿里云 Qwen 模型 |
| Vector DB | PGvector | - | AI 语义检索 |
| TTS | 阿里云 DashScope TTS | - | 语音合成（大脑执行） |
| ASR | 阿里云 DashScope ASR | - | 语音识别（大脑执行） |
| ID 生成 | 自增序列 / 雪花算法 | - | 业务表 `BIGINT GENERATED ALWAYS AS IDENTITY`，调用日志表雪花算法 |
| 加解密 | AES / RSA / HMAC-SHA256 | - |

---

## 8. 文档索引

| 文档 | 路径 | 说明 |
|------|------|------|
| 整体架构 | `docs/architecture-overview.md` | 本文档 |
| Backend 架构 | `docs/backend-architecture.md` | 中枢核心服务详细架构 |
| Edge 架构 | `docs/edge-architecture.md` | 大脑执行层详细架构 |
| Frontend 架构 | `docs/frontend-architecture.md` | 中枢管理界面详细架构 |
| 需求设计 | `docs/requirements-design.md` | 功能需求与业务设计 |
