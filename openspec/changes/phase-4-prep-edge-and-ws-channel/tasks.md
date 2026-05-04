## 1. Backend — 数据库与领域

- [x] 1.1 编写 `backend/src/main/resources/db/migration-postgresql/V5__init_brain_dispatch_log.sql`：含 id / msg_id / tenant_id / type / payload(jsonb) / status(varchar(16)) / last_error / created_by / created_time / updated_by / updated_time / acked_time，并加 `UNIQUE(msg_id)`、`INDEX(tenant_id, status, created_time DESC)`
- [x] 1.2 编写等价的 `backend/src/main/resources/db/migration-mysql/V5__init_brain_dispatch_log.sql`（payload 用 `json`，其余字段语义一致）
- [x] 1.3 本地启动 Backend，验证 Flyway 在 PostgreSQL 上完成迁移、表结构与索引符合预期
- [x] 1.4 新建包 `domain/braindispatch/po/`，编写 `BrainDispatchLogPO`（继承 `BaseAllUserTime`，使用 `@Table("t_brain_dispatch_log")` + `@Id`，payload 字段使用项目现有 JsonConverter）
- [x] 1.5 新建 `domain/braindispatch/entity/BrainDispatchType` 枚举（CONFIG_UPDATE / ACK / HEARTBEAT）与 `BrainDispatchStatus` 枚举（pending / sent / acked / failed / timeout）
- [x] 1.6 新建 `domain/braindispatch/entity/BrainDispatchLogEntity`，封装状态机方法 `markSent()` / `markAcked(now)` / `markFailed(code,msg)` / `markTimeout()`，对非法转移抛 `IllegalStateException`
- [x] 1.7 新建 `domain/braindispatch/repository/BrainDispatchLogRepository`（`@Component`，使用 `R2dbcEntityTemplate`），提供 `insert`、`findByMsgId`、`updateStatus`、`findSentBefore(threshold)` 等响应式方法
- [x] 1.8 新建 Backend 单元测试 `BrainDispatchLogEntityTests`：覆盖所有合法转移（pending→sent→acked / sent→failed / sent→timeout）与全部非法转移（终态再扭转、跨态跳跃）

## 2. Backend — Envelope 协议与 WebSocket 服务端

- [x] 2.1 在 `interfaces/dto/braindispatch/`（或同级 application 层目录）新增 `EnvelopeDTO`、`AckPayloadDTO`，对齐 design.md 协议契约；Jackson 配置 `FAIL_ON_UNKNOWN_PROPERTIES=true`
- [x] 2.2 新建 `infrastructure/config/ws/EdgeWebSocketConfiguration`：注册 `HandlerMapping`，将 `/ws/edge` 路由到自定义 `WebSocketHandler`
- [x] 2.3 实现 `infrastructure/ws/EdgeWebSocketHandler`（`WebSocketHandler`）：解析进站 envelope；`HEARTBEAT` 仅更新内存 lastSeen；`ACK` 走 `BrainDispatchService` 状态扭转；非法 envelope 仅 WARN
- [x] 2.4 实现 `infrastructure/ws/EdgeConnectionRegistry`（`@Component`）：登记/注销当前活跃 WebSocket session 的 `Sinks.Many<EnvelopeDTO>`，提供 `broadcast(envelope)` 给应用层调用
- [x] 2.5 在 `application/service/braindispatch/BrainDispatchService` 里实现：`createPending(tenantId, payload) → Mono<msgId>`、`onAck(envelope)`、`onSendSuccess(msgId)`，复用 `infrastructure/utils/id` 生成 msgId
- [x] 2.6 实现 `application/service/braindispatch/BrainDispatchTimeoutScanner`：每 5s 扫描 `status=sent && created_time <= now-30s` 并扭转为 `timeout`，使用 `Flux.interval` 在反应式调度器上运行
- [x] 2.7 单测：MockHandler 验证 ACK code=0/≠0 路径、未知 msgId 的 ACK 被忽略、错误 envelope 不影响连接

## 3. Backend — Internal API 与路由前缀

- [x] 3.1 在 `infrastructure/config/web/`（或现有注解所在目录）新增 `@RequestMappingInternalV1`，前缀 `/internal/v1`
- [x] 3.2 修改 `ContextPathConfiguration`：识别 `@RequestMappingInternalV1` 并按 `/internal/v1` 前缀注册路径
- [x] 3.3 新建 `interfaces/api/internal/InternalDispatchApi` 接口与 `interfaces/handler/internal/InternalDispatchHandler`，标注 `@RequestMappingInternalV1`，提供 `POST /dispatch/{tenantId}`，调用 `BrainDispatchService.createPending` 并触发 `EdgeConnectionRegistry.broadcast`
- [x] 3.4 单测/切片测试：验证 `/internal/v1/dispatch/{tenantId}` 在没有 Edge 连接时仍写入 pending 记录、不阻塞响应

## 4. Edge — 项目骨架与依赖

- [x] 4.1 在仓库根创建 `/edge/` 目录，初始化独立 Maven 项目（不进入 backend 多模块），`pom.xml` 引入 Spring Boot 4.0.6、`spring-cloud-starter-gateway` 4.x、`spring-boot-starter-webflux`、`reactor-netty-http`、Java 21、Lombok
- [x] 4.2 编写 `EdgeApplication.java` 与 `application.yaml`（含 `edge.backend-ws-url`、心跳间隔、退避配置；提供 `application-local.yaml` 默认连本机 8080）
- [x] 4.3 在 `/edge/` 复制并裁剪 `mvnw` / `mvnw.cmd` / `.mvn/` 包装器，确认 `./mvnw -v` 成功
- [x] 4.4 增加 `/edge/.gitignore`、README 占位（仅一句话说明 4-prep 用途，不重复 docs）

## 5. Edge — WebSocket 客户端与心跳

- [x] 5.1 实现 `EdgeWebSocketClient`：基于 `ReactorNettyWebSocketClient` 连 `${edge.backend-ws-url}/ws/edge`，连接生命周期暴露为 `Sinks.Many<EnvelopeDTO>` 出站 + `Flux<EnvelopeDTO>` 入站
- [x] 5.2 在客户端外层包一层 `Mono.retryWhen(Retry.backoff(...).maxBackoff(30s))`，初始 1s、倍增上限 30s；任何一次成功 onSubscribe 后复位计数（用 `Retry.backoff(...).filter(...)` 或自定义 `Retry`）
- [x] 5.3 实现 `EdgeMessageDispatcher`：按 envelope `type` 路由——`CONFIG_UPDATE` 4-prep 阶段仅日志 + 立即回 ACK code=0；`HEARTBEAT` 忽略；未知 type 回 ACK code=1002
- [x] 5.4 实现 msgId 去重 LRU（容量 1024），命中时仍回 ACK code=0 但不调用业务逻辑
- [x] 5.5 实现心跳任务：`Flux.interval(15s)` 推一条 `HEARTBEAT` envelope（`tenantId=null`、`payload=null`），由出站 sink 发出
- [x] 5.6 单元测试：envelope 序列化/反序列化、LRU 去重、CONFIG_UPDATE 自动 ACK、未知 type 回 1002

## 6. Backend — 监控用查询端点（支撑 Admin UI）

- [x] 6.1 在 `application/service/braindispatch/BrainDispatchQueryService` 实现：`pageLogs(filters, page, size) → Mono<Page<BrainDispatchLogBO>>`、`getLog(msgId) → Mono<BrainDispatchLogBO>`、`getEdgeStatus() → Mono<EdgeStatusBO>`，复用 `EdgeConnectionRegistry` 的内存数据
- [x] 6.2 扩展 `InternalDispatchHandler`（或新建 `InternalDispatchQueryHandler`）：暴露 `GET /dispatch/logs`（query `tenantId/status[]/from/to/msgId/page/size`）、`GET /dispatch/logs/{msgId}`、`GET /edge/status`，仍使用 `@RequestMappingInternalV1`
- [x] 6.3 `WebTestClient` 切片测试覆盖三端点：`status` 多值过滤、`from/to` 时间区间、单条详情 payload 不截断、无 Edge 连接时 `edge/status` 返回 `connected=false sessionCount=0 lastSeenAgoMs=null`

## 7. Frontend Admin — Dispatch Logs 页与 Edge 状态徽标

- [x] 7.1 在 `frontend/packages/api-client` 增加 `dispatchApi`：`listLogs(params)`、`getLog(msgId)`、`postDispatch(tenantId, payload)`、`getEdgeStatus()`，对齐已有 axios baseURL/拦截器风格
- [x] 7.2 在 `frontend/apps/admin/hooks/` 新增 `use-dispatch-logs.ts`（`useQuery` + `refetchInterval: 5_000`）、`use-edge-status.ts`（`refetchInterval: 2_000`），key 命名与现有 `use-admin-tenants` 风格一致
- [x] 7.3 新建路由 `frontend/apps/admin/app/(dashboard)/dispatch-logs/page.tsx`：antd `PageHeader` + 行内筛选 `Form` + `Table`（rowKey=`msgId`，列序按 design.md 表格冻结）+ 分页；筛选参数同步到 URL search params（`useSearchParams`）
- [x] 7.4 实现状态色映射常量 `dispatchStatusTagProps` 集中在 `frontend/apps/admin/app/(dashboard)/dispatch-logs/_components/StatusTag.tsx`（含 5 种 status，验证 antd `Tag` color 与文案）
- [x] 7.5 实现详情 Drawer 子组件 `DispatchDetailDrawer.tsx`：`Descriptions` + `pre` 块 JSON + 复制按钮 + 失败时 `Alert`；4-prep **不**含重发按钮
- [x] 7.6 实现测试下发 Modal `TestDispatchModal.tsx`：`tenantId InputNumber min=1` + `payload TextArea`；提交前 `JSON.parse` 校验，失败显示 `Form.Item` 错误；成功后 `message.success` + `queryClient.invalidateQueries(['dispatchLogs'])`
- [x] 7.7 在 `(dashboard)/layout.tsx` 顶部右侧插入 `<EdgeStatusBadge />`（新建 `frontend/apps/admin/app/(dashboard)/_components/EdgeStatusBadge.tsx`）：3 档状态点 + Tag + `aria-live="polite"`，骨架 16×72px
- [x] 7.8 在 `globals.css` 增加 `prefers-reduced-motion` 规则（如已有则跳过）；< 1024px 在 `dispatch-logs/page.tsx` 渲染 antd `Empty` + "请使用桌面端访问"
- [x] 7.9 React Testing Library 单测：StatusTag 5 状态快照、DispatchDetailDrawer 渲染含 payload 的 fixture、TestDispatchModal 非法 JSON 不触发请求、EdgeStatusBadge 三档状态切换 aria-live 正确
- [x] 7.10 `pnpm --filter @lazyday/admin lint` 与 `pnpm --filter @lazyday/admin build` 本地通过

## 8. 端到端集成测试

- [x] 8.1 在 `/edge/` 编写一个 Spring `@SpringBootTest` 集成测试：使用嵌入式 Backend（或 testcontainers + Backend Jar）启动 `/ws/edge`，验证 Edge 5s 内连上
- [x] 8.2 测试场景 A：通过 `WebTestClient` 调 `POST /internal/v1/dispatch/{tenantId}`，断言 1s 内 `t_brain_dispatch_log.status=acked`
- [x] 8.3 测试场景 B：模拟 Edge 不回 ACK，等待 30~35s 后断言记录扭转为 `timeout`
- [x] 8.4 测试场景 C：同一 msgId 连续推两次，断言 Edge 仅触发一次业务回调，但回了两次 ACK
- [x] 8.5 测试场景 D：杀掉 Backend 30s 后重启，断言 Edge 在重启后 30s 内自动重连
- [x] 8.6 在仓库根编写说明：`./mvnw test`（backend）+ `./mvnw test`（edge）+ `pnpm --filter @lazyday/admin test/build` 三段命令清单（合并到本变更 tasks.md 完成记录或 admin app README，本期不动 docs/）

## 9. 验收与归档准备

- [x] 9.1 跑通 `cd backend && ./mvnw test`、`cd edge && ./mvnw test`、`pnpm --filter @lazyday/admin lint && pnpm --filter @lazyday/admin build`，全部绿色
- [x] 9.2 手工演练（端到端 echo）：在 admin `/dispatch-logs` 点 "测试下发"，租户 1 + payload `{"hello":"edge"}`；观察表格 5s 内出现新行并从 pending → sent → acked，详情 Drawer 内 payload JSON 正确
- [x] 9.3 手工演练（timeout）：关闭 Edge 进程后再次"测试下发"；观察 30s 后该行自动从 `sent` 变为 `timeout`，`lastError` 列展示 "ack timeout"
- [x] 9.4 手工演练（msgId 幂等）：通过 Backend 注入或 Edge mock 让同一 msgId 重发两次，确认 list 只有一条记录、status 仍为 acked，不会出现重复行
- [x] 9.5 手工演练（自动重连）：`kill -9` Backend，观察 admin 顶部 Edge 状态徽标在 ≤2s 内变 `disconnected`；重启 Backend 后 ≤30s 内徽标变回 `connected`，再次 "测试下发" 仍能正常 ack
- [x] 9.6 自检：design.md 协议契约一节、UI 设计契约一节是否覆盖了 4a / 4b / 4e 可能引用的所有字段与状态色映射；如有遗漏补回 design.md
- [x] 9.7 提交 PR / commit；不在本期跑 `openspec archive`，archive 留给变更落地后再执行

## 验证命令清单

- Backend：`cd backend && ./mvnw test`
- Edge：`cd edge && ./mvnw test`
- Admin：`cd frontend && pnpm --filter @lazyday/admin test && pnpm --filter @lazyday/admin lint && pnpm --filter @lazyday/admin build`

## 验收证据

- 8.1：`cd edge && ./mvnw -Dtest=EdgeWebSocketClientIntegrationTest test` 覆盖 Edge 在 5s 内连上模拟 Backend `/ws/edge`，收到 `CONFIG_UPDATE` 后回 `ACK code=0`。
- 8.2 / 9.2：`cd backend && ./mvnw -Dtest=BrainDispatchChannelIntegrationTest test` 中 `dispatchPost_whenEdgeConnected_shouldAckWithinOneSecond` 覆盖内部下发、WebSocket 发出、ACK 回写、payload JSON 正确。
- 8.3 / 9.3：同一测试类中 `dispatchPost_whenEdgeDoesNotAck_shouldTimeoutAfterThirtySeconds` 覆盖无 ACK 后 30~35s 扭转 `timeout`，`lastError="ack timeout"`。
- 8.4 / 9.4：同一测试类中 `duplicateAck_shouldKeepSingleAckedRecord` 覆盖重复 ACK 后库内仅一条 `msg_id` 记录，状态保持 `acked`。
- 8.5 / 9.5：`EdgeWebSocketClientIntegrationTest.connectForever_shouldReconnectAfterConnectionCompletes` 覆盖 Edge 正常完成连接后自动重连；`BrainDispatchChannelIntegrationTest.edgeReconnect_shouldRestoreSessionWithinThirtySeconds` 覆盖重连后再次下发仍可 ACK。
- Admin UI 可视化：`pnpm --filter @lazyday/admin test` 覆盖 `StatusTag` 五状态、详情 Drawer payload、测试下发 Modal 非法 JSON 阻断、`EdgeStatusBadge` 三档状态和 `aria-live`。
