## Context

Lazyday 后端是 Spring Boot 4.0.6 reactive（WebFlux + R2DBC）的 DDD 4 层架构（参见 `.claude/CLAUDE.md`）。前三期已落地租户域、配额/调用日志、Webhook 投递（见已 archive 的 2026-04-27 / 2026-04-29-2a / 2026-04-29-3 三个变更）。Phase 4 计划在 `docs/iteration-plan.md §3.4` 拆为 `4-prep / 4a / 4b / 4c / 4d / 4e` 六个子迭代，本变更（4-prep）是其余五个的承重墙：必须先冻结 Backend↔Edge 长连接通道、协议契约和落库结构，否则后续迭代会反复修改协议。

当前仓库结构是 `/backend/` 单 Maven 项目；Edge 层尚未存在；Flyway 迁移已到 V4。`infrastructure/utils/id` 已具备雪花 ID 生成能力，可直接复用为 `msgId` 来源。Phase 1 文档明确将 Edge 描述为“仓库根并列的独立项目”，不进入 backend 多模块。

主要约束：
- **反应式**：所有 Backend 路径必须返回 `Mono`/`Flux`，禁止阻塞调用。
- **DDD 4 层**：domain 包按 `entity / po / repository` 三件套，对齐已有的 `domain/webhook/`。
- **多 dialect**：迁移脚本需要在 `db/migration/`（公共）或 `db/migration-postgresql/`、`db/migration-mysql/` 下提供等价 DDL；当前 Phase 4-prep 的 jsonb/json 字段需要分 dialect 落地。
- **不引入新的下游依赖**到 Backend，Edge 才是新项目。

## Goals / Non-Goals

**Goals:**
- Backend 暴露稳定的 `/ws/edge` WebSocket 端点，能接受 Edge 主动连入并完成双向消息收发。
- 冻结 envelope 协议（字段、type 枚举、msgId 规范、错误码、超时语义、状态图）—— 4a / 4b 等只引用本节，不再讨论。
- 落地 `t_brain_dispatch_log` 表与 `pending → sent → (acked | failed | timeout)` 状态机；所有终态终止；重下发新建记录而不修改旧记录。
- 提供内部下发占位接口 `POST /internal/dispatch/{tenantId}`，4-prep 仅 echo 通；为 4b 留出对接口形。
- 新建 `/edge/` 顶级 Maven 项目，含启动器、WebSocket 客户端（指数退避）、消息分发器、心跳任务，能够在本地与 Backend 完成端到端连通。
- 端到端验收：Edge 启动 5s 内连上、断网 30s 内重连、`POST /internal/dispatch/{tenantId}` 1s 内闭环 ACK、超时正确扭转状态、相同 msgId 幂等只 ACK 一次。

**Non-Goals:**
- TenantBrainConfig 快照组装（→ 4a-config）。
- Edge 端 L1/L2 缓存与“大脑”组装（→ 4b-edge）。
- AI Open API 业务接口与 Workflow 编排（→ 4c / 4d）。
- Admin 下发监控页面、自动重投策略（→ 4e-ops，由人工触发新建 pending 记录）。
- pgvector 的 R2DBC codec（→ 4a 与 ai_config 一起处理）。
- 任何前端 / Console 改动。
- Edge 运行多实例下的连接路由/亲和（4-prep 假设 Edge 单实例本地启动即可）。

## Decisions

### 决策记录表

| #  | 决策          | 选择                                                       | 理由                                                                          | 备选与拒因 |
|----|---------------|------------------------------------------------------------|-------------------------------------------------------------------------------|------------|
| B1 | 协议帧        | 单 envelope JSON（CONFIG_UPDATE / ACK / HEARTBEAT 共用） | 单连接简化客户端；msgId 做幂等                                                | 多通道（控制/数据分离）：增加客户端复杂度，4-prep 不必要 |
| B2 | 连接方向      | Edge 主动连 Backend                                        | Edge 是网关入口，Backend 触达不到（Edge 可能位于客户网络/反向代理后）          | Backend 推 Edge：拓扑不通 |
| B3 | 持久化        | Backend 落 `t_brain_dispatch_log`，状态终态终止           | Admin 监控/重放/可观测；状态机简单可证                                        | 仅内存：失去 Admin 监控视角；Kafka 队列：4-prep 不引新依赖 |
| B4 | Edge 位置     | 仓库根 `/edge/` 独立 Maven 项目                            | 与 Phase 1 文档语义一致，避免与 backend 多模块耦合，依赖独立演进              | 进入 backend 多模块：耦合 R2DBC/Flyway，Edge 不需要 |
| B5 | ACK 策略      | 仅记录，不自动重投                                         | 简化复杂度；失败由 Admin 在 4e-ops 手动触发重下发，新建 pending 记录保留轨迹 | 自动重投：需要重试调度器与去重幂等设计，4-prep 不做 |
| B11 | UI 栈        | 复用现有 `frontend/apps/admin/`：Next.js 15 + React 19 + Ant Design 5 + TanStack Query | 项目 admin app 已经在跑这套栈（见 `apps/admin/package.json`），引入 shadcn/Tailwind 会形成双轨组件库与设计 token 冲突 | shadcn+Tailwind：与已落地的 antd 双轨；纯 Vue：跨 SPA 拆分，运维成本大 |
| B12 | UI 范围      | 仅 dispatch 日志列表（只读 + 详情 Drawer）+ 测试下发 Modal + Edge 连接状态徽标 | 4-prep 的 UI 目标只是"让 spec 的四条验收标准可视化"，重发/批量/审计/告警是 4e-ops 范围 | 一次性把 4e-ops 全部 UI 做掉：放大爆炸半径，违背 4-prep "承重墙"定位 |
| B13 | UI 数据获取  | TanStack Query + 5s `refetchInterval` 轮询 list；Edge status 2s 轮询 | 与现有 `use-admin-tenants` / `use-plans` 的 react-query 风格一致；4-prep 不需要引 SSE/WebSocket 客户端 | SSE/WS 客户端：增加前端复杂度，4-prep 阶段 5s 轮询足够观察 pending→sent→acked 流转 |

### B6 — Internal 注解归属

项目当前已有 `@RequestMappingApiV1 / @RequestMappingApiV2 / @RequestMappingOpenV1` 三个上下文路由前缀注解，由 `ContextPathConfiguration` 解析。**新增** `@RequestMappingInternalV1`，前缀 `/internal/v1`。Internal 入口在 4-prep 是占位 echo，但同样需要前缀语义统一；新增注解优于把 internal 路径塞进 OpenV1（后者对外、对鉴权策略也不一样）。注解放在 `infrastructure/config/web/` 同级目录。

### B7 — 迁移脚本编号与 dialect

新增 `V5__init_brain_dispatch_log.sql`：
- 公共字段（id, msg_id, tenant_id, type, status, last_error, created_time, updated_time, acked_time, created_by, updated_by）放 `db/migration-postgresql/V5__init_brain_dispatch_log.sql` 与 `db/migration-mysql/V5__init_brain_dispatch_log.sql`。
- `payload` 字段：PostgreSQL 用 `jsonb`；MySQL 用 `json`。`status` 字段使用 `varchar(16)`（不使用 PostgreSQL `enum`，避免迁移依赖）。
- 索引：`UNIQUE(msg_id)`、`INDEX(tenant_id, status, created_time DESC)`。
- 字段命名遵循 `BaseAllUserTime`（snake_case + `created_by` / `updated_by`）。

### B8 — Heartbeat 与 timeout 实现

- Edge 每 15s 主动发 `HEARTBEAT`，作为存活信号。
- Backend 每 5s 跑一次扫描任务（`Scheduler` 或 `Flux.interval` + R2DBC 查询）：把 `status=sent` 且 `created_time <= now-30s` 的记录扭转到 `timeout` 并写入 `last_error`。
- HEARTBEAT 不入库，仅在内存里更新连接的 lastSeen；连接断开（或 30s 无 HEARTBEAT）时，由扫描任务负责状态扭转（HEARTBEAT 缺失 ≠ 自动失败 — 仅 `status=sent` 的下发记录会扭转为 timeout）。

### B9 — msgId 与幂等

- `msgId` 在 Backend 由雪花 ID 生成（`infrastructure/utils/id`），写入 `t_brain_dispatch_log.msg_id`，UNIQUE。
- Edge 维护近 1024 条 `msgId` 的去重 LRU；同 msgId 重发时仅回 ACK，不再触发业务。
- ACK envelope 必须回带原 `msgId`，Backend 通过 `msg_id` 定位记录扭转 `status=acked`。

### B10 — Edge 重连退避

`1s → 2s → 4s → 8s → 16s → 30s（封顶）`。任意一次成功连接复位为 1s。退避在 `EdgeWebSocketClient` 内基于 `Mono.retryWhen(Retry.backoff(...))` 实现，无需额外调度器。

## 协议契约（envelope 冻结条款）

> 本节是 4-prep 的产出契约，4a-config / 4b-edge 等子迭代直接引用，不再讨论字段语义。

### Envelope 字段

```jsonc
{
  "type": "CONFIG_UPDATE | ACK | HEARTBEAT",   // 必填
  "msgId": "1888..."  /* 雪花 ID 字符串 */,    // 必填
  "tenantId": 123                              /* Long */, // CONFIG_UPDATE/ACK 必填；HEARTBEAT 允许为 null
  "payload": { /* type 相关，见下 */ }          // CONFIG_UPDATE 必填；ACK/HEARTBEAT 允许 null
}
```

- 全部字段大小写敏感；Backend 和 Edge 都使用 Jackson `SnakeCaseStrategy=OFF`、`FAIL_ON_UNKNOWN_PROPERTIES=true`。
- 顶层不允许新增字段；新业务字段一律放 `payload` 内。

### type 枚举

| type            | 方向          | payload                                                              |
|-----------------|---------------|----------------------------------------------------------------------|
| CONFIG_UPDATE   | Backend → Edge | 4-prep：任意 JSON（echo from `POST /internal/dispatch/{tenantId}`）。4a 起：`TenantBrainConfig` 快照对象 |
| ACK             | Edge → Backend | `{ "ackedMsgId": "...", "code": 0, "message": "ok" }`               |
| HEARTBEAT       | 双向          | null                                                                 |

### 错误码

ACK envelope 的 `payload.code`：

- `0` — 成功（Edge 端接受了 CONFIG_UPDATE）。
- `1001` — payload schema 校验失败。
- `1002` — Edge 内部异常（含未知 type）。
- 其余保留。任何非 0 都会让 Backend 把对应记录扭转为 `failed` 并写入 `last_error = code:message`。

### 超时语义

- Backend 发出 CONFIG_UPDATE 后：`status=sent`，`created_time` 起 30s 内未收到对应 `ACK` → 扫描任务扭转 `status=timeout`。
- HEARTBEAT 间隔：Edge 15s；连续 2 个心跳缺失（即 30s+ 无任何消息）则视为连接死，由 `WebSocketHandler.handle()` 的下游 onError/onComplete 负责清理连接，本身不直接改写 dispatch 记录状态。

### 状态机

```
              发送                ACK code=0
   ┌────────┐  →   ┌──────┐  →   ┌───────┐
   │pending │      │ sent │      │ acked │  (终态)
   └────────┘      └──────┘      └───────┘
                       │
                       │  ACK code≠0
                       ├──────────────► failed   (终态)
                       │
                       │  30s 无 ACK
                       └──────────────► timeout  (终态)

   重下发：
   - 不修改任何已存在记录
   - 由 4e-ops Admin 触发：新建一条 status=pending 的新记录（新 msgId），
     payload 复制自原记录 + 标注 retry_of_msg_id（可选字段，4-prep 不做，先在 4e-ops 引入）
```

合法转移：`pending → sent`、`sent → acked`、`sent → failed`、`sent → timeout`。其余转移一律拒绝并抛 `IllegalStateException`，由领域层兜底。

## Admin UI / UX 设计契约

> 本节冻结 4-prep 阶段 Admin 监控 UI 的最小范围；4e-ops 在此之上扩展，**不得**降级或破坏本节定义的导航路径、字段顺序与状态色映射。

### 栈与对齐

- 路径：`frontend/apps/admin/app/(dashboard)/dispatch-logs/`（与现有 `tenants/`、`plans/`、`logs/` 同级）
- 组件库：复用 `antd@^5.24` + `@ant-design/icons` + `@lazyday/ui` 的 `LazydayProvider`，主色 `#722ed1` 不变
- 数据：`@tanstack/react-query@^5` + 工作区包 `@lazyday/api-client`（Axios），新建 hook `useDispatchLogs(filters)` 与 `useEdgeStatus()`
- 状态：与现有 admin 同级目录约定——筛选状态走 URL search params（避免 Zustand 全局污染），Drawer 打开行 id 走本地 `useState`
- 不引入：shadcn / Tailwind / 任何额外组件库 / D3.js / Recharts（4-prep 没有图表需求）

### 信息架构

- 全局：在 `(dashboard)/layout.tsx` 顶部右侧新增 **`<EdgeStatusBadge />`**——一个 antd `Tag` + 状态点：
  - `connected`（绿色 success：`#52c41a`）：文案 `Edge 在线 · {sessionCount} 个会话 · 心跳 {Xs} 前`
  - `stale`（橙色 warning：`#faad14`）：文案 `Edge 心跳延迟 ({Xs}s)`，阈值 lastSeen > 20s 且 ≤ 30s
  - `disconnected`（红色 error：`#ff4d4f`）：文案 `Edge 离线`
  - 每 2s 拉一次 `GET /internal/v1/edge/status`，无感骨架（首屏返回前 Tag 显示骨架占位 16×72px）
- 主页面 `/dispatch-logs`：单页面无子路由，自上而下三段：
  1. **页头**：`PageHeader`（左：标题"Dispatch Logs"+ 副标题"Backend → Edge 下发轨迹"；右：`Button type="primary"` "测试下发"，icon `<ThunderboltOutlined />`）
  2. **筛选条**：行内 antd `Form` `layout="inline"`：`tenantId` 数字输入；`status` 多选 `Select`（5 个状态项）；`createdRange` `RangePicker`；`msgId` `Input.Search`；`Button` "重置"
  3. **数据表**：antd `Table` + `Pagination`，rowKey=`msgId`；`scroll={{ x: 1100 }}`；`onRow` 触发详情 Drawer

### 表列定义（顺序冻结，不得调换）

| 列 | dataIndex | 渲染 | 宽度 |
|----|-----------|------|------|
| msgId | `msgId` | 等宽字体（与现有 logs 列保持一致），单击复制 | 200 |
| 租户 | `tenantId` | 数字 | 100 |
| 类型 | `type` | antd `Tag`（CONFIG_UPDATE / ACK / HEARTBEAT 中性色）| 130 |
| 状态 | `status` | antd `Tag` 带色（见下） | 110 |
| 创建时间 | `createdTime` | `dayjs.format('YYYY-MM-DD HH:mm:ss')` | 170 |
| ACK 时间 | `ackedTime` | 同上，空值 `—` | 170 |
| 错误 | `lastError` | 超过 30 字 `Tooltip` + `Typography.Text ellipsis` | 200 |
| 操作 | — | `Button type="link"` "详情" → 打开 Drawer | 80 |

### 状态色映射（与 antd `Tag` color 一一对应）

| status   | Tag color    | hex 参考  | 备注                                    |
|----------|--------------|-----------|-----------------------------------------|
| pending  | `default`    | `#d9d9d9` | 灰，等待发送                            |
| sent     | `processing` | `#1677ff` | 蓝，已发出未确认（带 antd 默认动效）    |
| acked    | `success`    | `#52c41a` | 绿，终态成功                            |
| failed   | `error`      | `#ff4d4f` | 红，终态业务失败                        |
| timeout  | `warning`    | `#faad14` | 橙，终态超时无 ACK                      |

### 详情 Drawer

- antd `Drawer` `placement="right" width={560}`
- 顶部 `Descriptions` 6 字段：msgId / tenantId / type / status (Tag) / createdTime / ackedTime
- 中部 `Typography.Title level={5}` "Payload" + `pre` 块包 JSON.stringify(payload, null, 2)，`Button` "复制 JSON"
- 底部 `Alert` 显示 `lastError`（仅当非空，type="error"）
- 4-prep 阶段**不**含"重发"按钮（4e-ops 范围）

### 测试下发 Modal

- antd `Modal` `width={520}`，触发自页头按钮
- 表单：`Form` 必填字段 `tenantId`（`InputNumber`，min=1）+ `payload`（`Input.TextArea` rows=8，placeholder：`{"hello": "edge"}`）
- 提交：调用 `POST /internal/v1/dispatch/{tenantId}`，前端 JSON.parse `payload`，失败时 `Form.Item` 显示 `JSON 不合法` 错误
- 成功后：关闭 Modal、`message.success('已下发，msgId=...')`、立即触发 `queryClient.invalidateQueries(['dispatchLogs'])` 让 list 立刻刷新（不等下次 5s 轮询）
- Button 在 pending 期间 `loading` + `disabled`，避免双发

### UX 规则落实（来自 ui-ux-pro-max 检查清单）

- **a11y**：所有状态以 Tag 色 + 文案双通道呈现（绿/红色盲友好）；Drawer 与 Modal 走 antd 默认 ESC 关闭与焦点陷阱；Edge 状态徽标加 `aria-live="polite"`，状态切换时屏阅器播报。
- **触发反馈**：Button > 300ms 操作显示 antd 内建 `loading`；表格 row 加 `cursor: pointer`；hover 用 antd 默认 row hover（不要自己写 transform 避免抖动）。
- **加载状态**：list 首屏 `Table.loading=true` 显示 antd 骨架；Drawer 详情请求显示 `Spin`。
- **动效**：仅使用 antd 内建过渡（150–250ms）；尊重 `prefers-reduced-motion`，在 globals.css 加 `@media (prefers-reduced-motion: reduce) { * { animation-duration: 0.01ms !important; transition-duration: 0.01ms !important; } }`。
- **响应式**：admin 内部工具，目标分辨率 ≥1280×800；最小支持 1024px（< 1024px 显示 antd `Empty` + "请使用桌面端访问"）；不强制移动端适配。
- **图标**：仅使用 `@ant-design/icons`，禁止 emoji。
- **字体**：沿用 admin 现有字体栈（antd 默认 + 系统中文字体），不引入额外 Web Font，避免 FOIT。
- **文案语言**：简体中文，专业术语保留英文（msgId / payload / ACK / Edge）。

### 后端补充端点（仅为支撑 UI）

| Method | Path                                  | 用途                                              | 鉴权 |
|--------|---------------------------------------|---------------------------------------------------|------|
| GET    | `/internal/v1/dispatch/logs`          | 分页 list（query: `tenantId`, `status[]`, `from`, `to`, `msgId`, `page`, `size`）| 内部 |
| GET    | `/internal/v1/dispatch/logs/{msgId}`  | 单条详情（含 payload 全文）                       | 内部 |
| GET    | `/internal/v1/edge/status`            | 当前 Edge 连接概览 `{ connected, sessionCount, lastSeenAgoMs }` | 内部 |

> 这三个端点也归属 `@RequestMappingInternalV1`；4-prep 鉴权与下发占位接口同级（内网信任 / 监听 127.0.0.1），4e-ops 一并升级。

### 验收映射（UI 必须能可视化 spec.md 的全部场景）

| spec 场景 | UI 可视化路径 |
|-----------|---------------|
| 端到端 echo（pending → sent → acked ≤ 1s） | 测试下发 Modal 提交 → 表格在 ≤ 5s（首次 invalidate 立即触发，UI 实际 1–2s）内显示新行从 pending 流转到 acked |
| 30s 无 ACK → timeout | Edge 离线时测试下发 → 行进入 `sent` → 30s 后变成 `timeout` Tag，`lastError = "ack timeout"` |
| msgId 幂等 | Edge 模拟重复消息时，list 仍只有 **一行**（msg_id UNIQUE），且行 status 仍为 acked |
| 自动重连 | 模拟 Backend 重启：徽标 `connected → disconnected → connected`，过程中可见 30s 内恢复绿色 |
| 非法状态转移 | UI 不暴露重发按钮，因此用户路径上不可能触发非法转移；后端单测覆盖（不在 UI 验收范围） |

## Risks / Trade-offs

- **风险**：Edge 端 LRU 仅 1024 条，极端高吞吐下可能误判“非重复”。→ 缓解：4-prep 阶段下发频率极低（人工触发），1024 容量充足；4b 起若吞吐升高再扩。
- **风险**：HEARTBEAT 不入库，断开后仅靠内存连接状态识别。→ 缓解：扫描任务以 `t_brain_dispatch_log.status=sent` 为准独立判断 timeout；连接活性问题不污染业务表。
- **风险**：单 Backend 实例时 WebSocket session 仅在内存，重启会丢；记录依然在表里。→ 缓解：扫描任务会在重启后将悬挂的 `sent` 记录扭转为 `timeout`，不会出现“永久 sent”。
- **风险**：Edge 与 Backend 版本不一致时 envelope 校验失败。→ 缓解：错误码 1001 + design 文档冻结契约；后续迭代仅扩 `payload` 不动顶层。
- **Trade-off**：选择“仅记录、不自动重投” → 牺牲全自动恢复以换取 4-prep 阶段的实现简洁与可观测；正式自动重投放到 4e-ops 评估。
- **Trade-off**：Edge 独立 Maven 项目 → 需要双 build 命令；为换取依赖独立、与 backend R2DBC/Flyway 解耦，是值得的。
- **风险**：UI 用 5s 轮询而非 SSE，"pending → sent" 的中间态可能在 list 上一闪即过（毫秒级）。→ 缓解：详情 Drawer 通过 `acked_time - created_time` 与 `last_error` 仍能事后还原轨迹；4b/4e 视需要再升级到 SSE。
- **风险**：Edge 状态徽标轮询每 2s 一次会带来 admin 进程 30 req/min/用户。→ 缓解：endpoint 仅查内存连接 registry，O(1) 开销可忽略；4e-ops 评估是否合并到全局 SSE。
- **Trade-off**：放弃 shadcn/Tailwind 提案 → 与已有 antd 栈对齐，避免双组件库 token 冲突与设计割裂；牺牲了 shadcn 的更现代视觉感，但 admin 是内部工具，一致性 > 视觉新颖度。

## Migration Plan

1. **Backend**
   1. 落 `V5__init_brain_dispatch_log.sql`（pg/mysql 双 dialect），本地 `./mvnw spring-boot:run` 验证 Flyway 通过。
   2. 新增 `domain/braindispatch/`、WebSocket 配置 + Handler、`@RequestMappingInternalV1`、内部 echo API。
   3. 单测覆盖状态机所有合法 / 非法转移。
2. **Edge**
   1. 新建 `/edge/` Maven 项目 + Spring Cloud Gateway 4.x + WebFlux 启动器。
   2. 实现 `EdgeWebSocketClient`（带退避）、`EdgeMessageDispatcher`、心跳任务。
   3. 集成测试：本地 `./mvnw test` 启动 Backend 并连通 echo。
3. **回滚**：迁移脚本与 Edge 项目互相独立——回滚时删除 Edge 子目录，并对 `t_brain_dispatch_log` 执行 `DROP TABLE`（在生产前阶段，4-prep 尚未对外）。

## Open Questions

- Internal API 的鉴权方案：4-prep 阶段先内网信任 / 仅监听 127.0.0.1，正式鉴权放在 4e-ops 与统一鉴权策略一并落地。
- Edge 多实例时的连接路由（亲和 / 单 leader）：留到 4b 评估，本期不解决。
- `retry_of_msg_id` 字段是否在 V5 就预留：本期不预留，由 4e-ops 用补充迁移脚本添加，避免 4-prep 引入未使用的字段。