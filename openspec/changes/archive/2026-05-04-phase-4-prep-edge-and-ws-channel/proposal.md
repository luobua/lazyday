## Why

Phase 4 的所有后续子迭代（4a-config / 4b-edge / 4c / 4d / 4e）都依赖 Backend↔Edge 之间的双向 WebSocket 通道；如果在每个子迭代里各自“顺手做掉”这堵承重墙，协议会反复抖动，状态机会被多次重写，回归成本会被复利放大。Phase 4-prep 的目的就是先把通道、协议、落库三件事一次性冻结，给 4a/4b 等后续提供稳定地基。

## What Changes

- 新建 Backend WebSocket 服务端 `/ws/edge`（基于 WebFlux WebSocketHandler），承载 `CONFIG_UPDATE` / `ACK` / `HEARTBEAT` 三种 envelope 消息。
- 新增持久化表 `t_brain_dispatch_log` 与对应迁移脚本 `V5__init_brain_dispatch_log.sql`，并落地状态机 `pending → sent → (acked | failed | timeout)`，所有终态终止，重下发只追加新记录。
- 新建 domain 包 `domain/braindispatch/`（entity / po / repository），结构对齐 Phase 3 的 webhook 域。
- 新增内部 API 占位 `POST /internal/dispatch/{tenantId}`，4-prep 阶段仅打通管道（echo payload 至 Edge），真实下发逻辑留给 4b。如项目尚无 internal 注解，则在 design.md 里提议新增 `@RequestMappingInternalV1`。
- 新建 `/edge/` 顶级独立 Maven 项目（与 `/backend/` 平级，**不**接入 backend 多模块），技术栈 Spring Cloud Gateway 4.x + WebFlux + Java 21；包含 `EdgeApplication`、`application.yaml`、`EdgeWebSocketClient`（Reactor Netty + 指数退避 1s→2s→4s→8s→30s 封顶）、`EdgeMessageDispatcher`（按 `type` 路由，4-prep 仅日志 + 立即 ACK）、15s 心跳任务。
- 冻结 envelope 协议契约（字段、type 枚举、msgId 规范、错误码、超时语义、状态图），写入 design.md 的协议契约一节，作为 4a / 4b 的引用源。
- **新增（4-prep 范围微扩）：Admin 最小可验收监控 UI**——`Dispatch Logs` 页 + 全局 `Edge 连接状态徽标`，复用现有 `frontend/apps/admin/` 的 Next.js 15 + React 19 + Ant Design 5 + TanStack Query 栈。该 UI 是 4-prep 验收所需（手工演练 pending → sent → acked、30s timeout、msgId 幂等、断线自动重连四条 spec 场景的可视化窗口），4e-ops 后续再扩重发/审计能力。
- **不在范围**：TenantBrainConfig 快照组装（→ 4a）、Edge L1/L2 缓存与大脑组装（→ 4b）、AI Open API（/ai/chat 等，→ 4c/4d）、Workflow 编排画布（→ 4d）、Admin 下发监控页的**重发/批量/审计/告警**深度功能（→ 4e）、pgvector R2DBC codec（→ 4a 与 ai_config 一起处理）。

## Capabilities

### New Capabilities
- `brain-dispatch-channel`: Backend↔Edge 长连接通道，包含 WebSocket 服务端、envelope 协议、dispatch 日志状态机、内部下发 API 占位与 Edge 客户端骨架的契约级要求；以及 Admin 最小监控 UI（dispatch 日志只读列表 + 测试下发入口 + Edge 连接状态徽标）。

### Modified Capabilities
<!-- 本次改动不修改任何已有 capability 的需求 -->

## Impact

- **新增模块**：`/edge/` 顶级 Maven 项目（Spring Cloud Gateway 4.x + WebFlux）。
- **Backend 代码**：新增 `domain/braindispatch/` 包；新增 WebSocket 配置与 Handler；新增 `interfaces/handler/internal/`（或等价层）下的内部 API；如需新增内部注解则同步加到 `infrastructure/config/web/`。
- **数据库迁移**：新增 `backend/src/main/resources/db/migration/V5__init_brain_dispatch_log.sql`（含 PostgreSQL/MySQL 两套等价 DDL，遵循现有 dialect 分目录约定）。
- **配置**：`application.yaml` 增加 WebSocket / Edge 相关属性（端口、心跳间隔、超时等）。
- **依赖**：Edge 项目首次引入 `spring-cloud-starter-gateway` 4.x、`reactor-netty-http` 等；Backend 复用现有 WebFlux/R2DBC，不新增第三方坐标。
- **前端 Admin（新增范围）**：在 `frontend/apps/admin/app/(dashboard)/` 下新增 `dispatch-logs/` 路由（list + detail Drawer + 测试下发 Modal），并在 dashboard layout 中嵌入 Edge 连接状态徽标；复用现有 antd `App`、`LazydayProvider primaryColor=#722ed1`、TanStack Query、`@lazyday/api-client`、`dayjs`，**不**引入 shadcn 或 Tailwind（避免与 antd 双轨）。
- **测试**：新增 Backend 状态机单测、Edge 启动+连通性集成测试、Admin Dispatch Logs 页 React Testing Library 渲染快照与轮询行为测试；目标 `./mvnw test` 与 `pnpm --filter @lazyday/admin test` 本地通过。
- **下游依赖**：4a / 4b 不再需要重复设计协议或表，直接消费 4-prep 冻结的契约；4e-ops 在本期 UI 之上扩展重发、批量、审计、告警等深度能力。