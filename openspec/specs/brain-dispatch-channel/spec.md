# brain-dispatch-channel Specification

## Purpose
TBD - created by archiving change phase-4-prep-edge-and-ws-channel. Update Purpose after archive.
## Requirements
### Requirement: Backend WebSocket 端点

Backend SHALL 在路径 `/ws/edge` 暴露一个 WebFlux WebSocket 端点，接受 Edge 客户端主动建立的 TCP/WebSocket 长连接，并在该连接上以 envelope JSON 形式收发 `CONFIG_UPDATE` / `ACK` / `HEARTBEAT` 三类消息。

#### Scenario: Edge 成功建连
- **WHEN** Edge 进程启动并向 `/ws/edge` 发起 WebSocket 升级请求
- **THEN** Backend 必须接受连接，并在 5 秒内通过该连接处理后续消息

#### Scenario: 拒绝非 envelope 消息
- **WHEN** Backend 在该连接上收到无法解析为 envelope JSON 的文本帧（缺少 `type` 或 `msgId` 字段）
- **THEN** Backend 必须不影响连接存活地丢弃该帧，并以 WARN 级别记录一次日志，不得改写任何 `t_brain_dispatch_log` 记录

### Requirement: Envelope 协议契约

系统（Backend 与 Edge）SHALL 使用以下统一的 envelope JSON 结构通信，不得在顶层新增字段：

```jsonc
{ "type": "CONFIG_UPDATE | ACK | HEARTBEAT",
  "msgId": "<snowflake string>",
  "tenantId": <Long | null>,
  "payload": { ... } | null }
```

- `type` 必填，枚举三选一。
- `msgId` 必填，字符串，由 Backend 雪花 ID 生成。
- `tenantId`：`CONFIG_UPDATE` / `ACK` 必填；`HEARTBEAT` 允许为 null。
- `payload`：`CONFIG_UPDATE` 必填；`ACK` 必填且包含 `ackedMsgId`、`code`、`message`；`HEARTBEAT` 必须为 null。

#### Scenario: CONFIG_UPDATE 字段缺失
- **WHEN** Edge 收到一个 `type=CONFIG_UPDATE` 但 `tenantId` 为 null 的 envelope
- **THEN** Edge 必须回 `ACK` 且 `payload.code=1001`、`payload.message` 含字段名，且不调用业务处理

#### Scenario: HEARTBEAT 携带 payload
- **WHEN** 任意一方收到 `type=HEARTBEAT` 但 `payload` 非 null 的 envelope
- **THEN** 接收方必须忽略 `payload` 字段，不得视为协议错误，不得断开连接

#### Scenario: ACK 必须回带 ackedMsgId
- **WHEN** Backend 收到 `type=ACK` 但 `payload.ackedMsgId` 缺失或为空
- **THEN** Backend 必须丢弃该 ACK 并以 WARN 记录，不得修改任何 `t_brain_dispatch_log` 记录

### Requirement: dispatch 日志状态机

Backend SHALL 在 `t_brain_dispatch_log` 表为每一条向 Edge 发出的 `CONFIG_UPDATE` 维护一条记录，其 `status` 字段必须遵循状态机：`pending → sent → (acked | failed | timeout)`。`acked` / `failed` / `timeout` 为终态，禁止任何后续转移；重下发只允许新建一条 `pending` 记录，不得修改已有记录。

#### Scenario: 创建下发记录
- **WHEN** Backend 接收到内部下发请求并构造出新的 envelope（携带新的 `msgId`）
- **THEN** Backend 必须先以 `status=pending` 写入 `t_brain_dispatch_log`，然后再尝试通过 WebSocket 发送

#### Scenario: 发送成功标记 sent
- **WHEN** Backend 把 envelope 成功写入 WebSocket 出站
- **THEN** 对应记录的 `status` 必须扭转为 `sent`

#### Scenario: 收到成功 ACK 标记 acked
- **WHEN** Backend 收到 `type=ACK` 且 `payload.code=0`，且 `payload.ackedMsgId` 命中一条 `status=sent` 的记录
- **THEN** Backend 必须把该记录扭转为 `acked` 并写入 `acked_time = 当前时刻`

#### Scenario: 收到失败 ACK 标记 failed
- **WHEN** Backend 收到 `type=ACK` 且 `payload.code≠0`，命中一条 `status=sent` 的记录
- **THEN** Backend 必须把该记录扭转为 `failed`，并把 `last_error` 写为 `code:message`

#### Scenario: 30 秒未 ACK 标记 timeout
- **WHEN** 一条记录处于 `status=sent` 且 `created_time` 距今超过 30 秒，仍未收到任何 ACK
- **THEN** Backend 后台扫描任务必须把该记录扭转为 `status=timeout`，并写入 `last_error="ack timeout"`

#### Scenario: 拒绝非法状态转移
- **WHEN** 任意代码尝试把一条 `status` 已为 `acked` / `failed` / `timeout` 的记录再次扭转
- **THEN** 领域层必须抛出 `IllegalStateException` 并保持原始状态不变

#### Scenario: 重下发只新建记录
- **WHEN** Admin 在 4e-ops 触发某条 `failed` / `timeout` 记录的“重下发”
- **THEN** Backend 必须为本次重下发新建一条 `msgId` 不同的 `status=pending` 记录，不得修改原记录的任何字段

### Requirement: msgId 幂等

`msgId` SHALL 在系统中唯一标识一次下发；Edge SHALL 对最近至少 1024 条 `msgId` 实施幂等去重。

#### Scenario: 重复 msgId 仅 ACK 不重复执行
- **WHEN** Edge 在短时间内收到两条 `msgId` 相同的 `CONFIG_UPDATE` envelope
- **THEN** Edge 必须仅回 ACK（`code=0`），不得对该 envelope 触发第二次业务处理

#### Scenario: msgId 落库唯一
- **WHEN** Backend 试图以已存在的 `msg_id` 写入 `t_brain_dispatch_log`
- **THEN** 数据库必须以唯一约束阻止该写入，应用层必须捕获并以 ERROR 记录后中止本次下发

### Requirement: Edge 主动连接与重连

Edge SHALL 主动连接 Backend 的 `/ws/edge`，连接断开后必须按指数退避策略 `1s → 2s → 4s → 8s → 16s → 30s` 重试，30 秒为上限；任意一次成功建连必须将退避复位为 1 秒。

#### Scenario: 启动后建连
- **WHEN** Edge 进程启动且 Backend 健康
- **THEN** Edge 必须在启动后 5 秒内成功建立 `/ws/edge` 长连接

#### Scenario: 断网后自动恢复
- **WHEN** Edge 与 Backend 之间的连接被中断（Backend 重启或网络抖动），Backend 在 30 秒内恢复
- **THEN** Edge 必须在 Backend 恢复后 30 秒内重新建立连接

### Requirement: 心跳与超时

Edge SHALL 每 15 秒在已建立的连接上发送一次 `type=HEARTBEAT` envelope；Backend SHALL 不得把缺失心跳直接当作业务失败，而是仅由扫描任务依据 `t_brain_dispatch_log.status=sent` + 30 秒规则判定 `timeout`。

#### Scenario: 周期性心跳
- **WHEN** Edge 与 Backend 已建立稳定连接 60 秒
- **THEN** Backend 必须在该窗口内至少观察到 3 次 `HEARTBEAT`，并且不得对任何 `dispatch` 记录扭转状态（仅心跳不触发状态变更）

#### Scenario: 心跳停止不直接改业务记录
- **WHEN** Edge 因故停止发送心跳超过 30 秒，但当前不存在 `status=sent` 的记录
- **THEN** Backend 不得对任何 `t_brain_dispatch_log` 记录进行写入；连接清理由 WebSocket 层独立完成

### Requirement: 内部下发占位接口

Backend SHALL 暴露 `POST /internal/v1/dispatch/{tenantId}`（前缀来自新增的 `@RequestMappingInternalV1` 注解），接收任意 JSON `payload`，在 4-prep 阶段仅作为通道连通性测试入口：以新雪花 ID 创建 `t_brain_dispatch_log` 记录、向当前 Edge 连接广播一条 `CONFIG_UPDATE` envelope（payload 原样透传），并以 `Mono<Void>` / 200 响应。

#### Scenario: 端到端 echo
- **WHEN** 调用方在 Edge 已连通时向 `POST /internal/v1/dispatch/{tenantId}` 提交任意合法 JSON `payload`
- **THEN** Backend 必须在 1 秒内通过 WebSocket 把 envelope 发送给 Edge，Edge 必须在 1 秒内回 `ACK code=0`，对应记录最终状态必须为 `acked`

#### Scenario: 无 Edge 连接时
- **WHEN** 调用方在没有任何 Edge 连接活跃时调用该接口
- **THEN** Backend 必须仍然写入 `status=pending` 记录，但不得阻塞响应；该记录将在 30 秒后被扫描任务扭转为 `timeout`

### Requirement: Edge 项目位置与栈

Edge SHALL 作为仓库根目录下与 `/backend/` 平级的独立 Maven 项目存在于 `/edge/`，使用 Spring Cloud Gateway 4.x + WebFlux + Java 21；不得作为 Backend 的 Maven 子模块加入。

#### Scenario: 独立 build
- **WHEN** 在 `/edge/` 目录下执行 `./mvnw clean package`
- **THEN** Edge 项目必须在不依赖 `/backend/` 任何源码的前提下构建成功

#### Scenario: 端到端集成测试
- **WHEN** 开发者在本地启动 Backend 并在 `/edge/` 目录运行 `./mvnw test`
- **THEN** Edge 的连通性集成测试必须能成功建立 `/ws/edge` 连接、收到一次模拟 `CONFIG_UPDATE` 并回 `ACK code=0`

### Requirement: 监控用查询端点

Backend SHALL 暴露三个内部查询端点，仅供 Admin Console 监控使用，前缀均为 `/internal/v1`：`GET /dispatch/logs`（分页 list，支持 query 参数 `tenantId`/`status[]`/`from`/`to`/`msgId`/`page`/`size`）、`GET /dispatch/logs/{msgId}`（单条含 payload 全文）、`GET /edge/status`（返回 `{ connected: boolean, sessionCount: number, lastSeenAgoMs: number | null }`）。

#### Scenario: 列表按状态过滤
- **WHEN** 调用方请求 `GET /internal/v1/dispatch/logs?status=pending&status=sent&page=1&size=20`
- **THEN** 响应必须仅包含 `status` 为 `pending` 或 `sent` 的记录，按 `created_time DESC` 排序，且包含 `total` 字段供分页使用

#### Scenario: 单条详情包含 payload
- **WHEN** 调用方请求 `GET /internal/v1/dispatch/logs/{msgId}`，且 msgId 存在
- **THEN** 响应必须包含完整的 `payload` JSON 全文（不得截断）以及 `lastError`（可能为 null）

#### Scenario: Edge 状态在无连接时
- **WHEN** 当前没有任何 Edge WebSocket 连接活跃，调用方请求 `GET /internal/v1/edge/status`
- **THEN** 响应必须为 `{ "connected": false, "sessionCount": 0, "lastSeenAgoMs": null }`

### Requirement: Admin Dispatch Logs 监控页

Admin Console SHALL 在 `/dispatch-logs` 路径下提供一个最小可用的 dispatch 日志监控页，复用 `frontend/apps/admin/` 现有 Next.js 15 + React 19 + Ant Design 5 + TanStack Query 栈，不得引入第二套组件库（如 shadcn / 自建 Tailwind 设计系统）。该页面 SHALL 通过现有 `@lazyday/api-client` 调用前述监控查询端点，并在表格中可视化每条记录的 `msgId / tenantId / type / status / createdTime / ackedTime / lastError`。

#### Scenario: 列表自动刷新观察状态流转
- **WHEN** 用户停留在 `/dispatch-logs` 页面，并且后台某条记录在外部从 `pending` 流转为 `acked`
- **THEN** 页面表格必须在 5 秒内自动刷新并展示该条记录的最新状态（通过 TanStack Query `refetchInterval` 或显式 `invalidateQueries` 实现）

#### Scenario: 状态色与 Tag 语义
- **WHEN** 表格渲染任意一行的 `status` 单元格
- **THEN** 必须使用 antd `Tag` 组件，并满足映射 `pending=default`、`sent=processing`、`acked=success`、`failed=error`、`timeout=warning`，且每个 Tag 都同时包含可读文案，不得仅以颜色区分（色盲可读性）

#### Scenario: 详情 Drawer 展示完整 payload
- **WHEN** 用户单击表格任意一行的"详情"按钮
- **THEN** 必须弹出 antd `Drawer`（`placement="right"` 宽 ≥ 480px），展示该记录的全部字段以及格式化（含缩进）的 `payload` JSON 全文，并提供"复制 JSON"按钮

#### Scenario: 测试下发触发可视轨迹
- **WHEN** 用户在该页面点击"测试下发"按钮，输入合法 `tenantId` 与合法 JSON `payload` 后提交
- **THEN** 前端必须调用 `POST /internal/v1/dispatch/{tenantId}`，提交成功后 5 秒内表格顶部必须出现新增的一行，并能观察到 `status` 从 `pending` / `sent` 流转到 `acked`（在 Edge 连通条件下）

#### Scenario: 非法 JSON 阻止提交
- **WHEN** 用户在测试下发 Modal 中输入无法 `JSON.parse` 的 payload 文本并尝试提交
- **THEN** 前端必须在 `Form.Item` 上显示错误提示且**不**发起 HTTP 请求，提交按钮保持可用以便用户更正

#### Scenario: 筛选状态保留在 URL
- **WHEN** 用户设置 `status=failed` 与 `tenantId=42` 后刷新浏览器
- **THEN** 页面必须从 URL search params 还原筛选条件，并以相同参数重新查询列表（避免依赖客户端内存状态）

### Requirement: Edge 连接状态徽标

Admin Console SHALL 在 `(dashboard)/layout.tsx` 顶部全局插入一个 Edge 连接状态徽标，每 2 秒轮询 `GET /internal/v1/edge/status`，根据响应渲染三档视觉状态：`connected`（绿）/`stale`（橙，lastSeenAgoMs ∈ (20000, 30000]）/`disconnected`（红）；徽标 SHALL 同时通过文案与颜色双通道呈现状态信息，不得仅以颜色区分。

#### Scenario: Edge 在线徽标
- **WHEN** `GET /internal/v1/edge/status` 返回 `{ connected: true, sessionCount: 1, lastSeenAgoMs: 3000 }`
- **THEN** 徽标必须显示绿色状态点 + 文案包含 "在线" 与会话数 `1`

#### Scenario: Edge 离线徽标
- **WHEN** 连续两次轮询返回 `connected=false`
- **THEN** 徽标必须显示红色状态点 + 文案 "Edge 离线"，并设置 `aria-live="polite"` 属性以便屏幕阅读器播报状态切换

#### Scenario: 徽标在 Backend 重启窗口的恢复
- **WHEN** Backend 重启过程中徽标从 `connected` 变为 `disconnected`，30 秒内 Backend 恢复且 Edge 重连成功
- **THEN** 徽标必须在 Backend 恢复后的下一次轮询周期（≤ 2 秒）内回到 `connected` 状态

### Requirement: Internal 路由前缀注解

Backend 项目 SHALL 引入新的注解 `@RequestMappingInternalV1`，前缀为 `/internal/v1`，由现有的 `ContextPathConfiguration` 解析；该注解必须仅用于内部下发等非对外接口，不得用于面向租户的 API。

#### Scenario: 注解被识别
- **WHEN** 一个 Handler 类标注 `@RequestMappingInternalV1` 并定义 `POST /dispatch/{tenantId}`
- **THEN** 实际生效的对外路径必须是 `/internal/v1/dispatch/{tenantId}`

