# Backend 技术架构（中枢核心服务）

> 版本：v0.5
> 日期：2026-04-25
> 作者：fanqibu

---

## 修订记录

| 版本 | 日期 | 修订内容 |
|------|------|---------|
| v0.5 | 2026-04-25 | ID 策略统一为 BIGINT 自增序列（§4.2、§5.x 全量修改）；§5.1 移除 password_hash（密码存 t_user）；§9 WebSocket 代码改为 WebFlux 原生 WebSocketHandler；§5.9 HTML 残留清理；配置粒度说明补充 |
| v0.4 | 2026-04-25 | **L-1**：`ConfigDispatchAggregation` 命名建议与「大脑配置」语义对齐，标注建议名称 `BrainConfigAggregation`（可选采纳）；引入「中枢定位」章节，明确 Backend 为中枢核心服务，不执行 AI 推理；补充 WebSocket 配置下发通道说明；tts_config 字段对齐（pitch/volume） |
| v0.3 | 2026-04-25 | 新增 RAG 配置域、Agent 配置域、Workflow 配置域、配置下发域 |
| v0.2 | 2026-04-25 | 补充配额域、Webhook 域详细设计 |
| v0.1 | 2026-04-25 | 初始版本，包含租户域、AppKey 域、调用日志域 |

---

## 0. 中枢定位

Backend 是**中枢（Central）的核心服务层**，在整个架构中的角色如下：

```
┌─────────────────────────────────────────────────────────────┐
│                        中枢（Central）                        │
│                                                             │
│   Frontend（管控台）  ←→  Backend（核心服务）  ←→  Edge（大脑） │
│                                                             │
│   · 不直接执行 AI 推理                                       │
│   · 负责：业务逻辑 + 配置管理 + 配置下发                      │
│   · 通过 WebSocket 向 Edge 大脑 下发 RAG/Agent/Workflow 配置  │
└─────────────────────────────────────────────────────────────┘
```

Backend 的职责边界：
- ✅ **做**：业务逻辑、配置 CRUD、配置组装、WebSocket 下发、调用日志存储、Webhook 推送
- ❌ **不做**：AI 推理执行（RAG 检索/Agent 执行/Workflow 运行/TTS/ASR），这些由 Edge 大脑执行

---

## 1. 技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 框架 | Spring Boot | 4.0.6 | 最新版，响应式支持 |
| Web | Spring WebFlux | 6.x | 全异步非阻塞 |
| 数据访问 | Spring Data R2DBC | 3.x | 响应式数据库访问 |
| 连接池 | R2DBC Pool | 1.0.2 | 响应式连接池 |
| 数据库迁移 | Flyway | - | 多方言支持 |
| 数据库 | PostgreSQL | 16.x | 主数据库 |
| 向量数据库 | PGvector | - | AI 语义检索 |
| AI | Spring AI + DashScope | 2.0.0-M4 | 阿里云 Qwen 模型 |
| 对象映射 | MapStruct | 1.5.5 | 编译期对象转换 |
| 代码简化 | Lombok | 1.18.x | 注解驱动 |
| ID 生成 | 自增序列 / Snowflake | - | 业务表用 `BIGINT GENERATED ALWAYS AS IDENTITY`，调用日志表用雪花算法（应用层生成） |
| 加解密 | AES / RSA / HMAC | 自实现 | 接口签名使用 |
| 工具库 | Commons Lang3 / IO | 3.20 / 2.21 | Apache 工具库 |

---

## 2. 分层架构（DDD）

```
┌──────────────────────────────────────────────────────┐
│                   interfaces 层                       │
│   api/          定义接口契约（interface）               │
│   handler/      Controller 实现（@RestController）    │
│   request/      请求 DTO                              │
│   response/     响应 DTO                              │
└─────────────────────────┬────────────────────────────┘
                          │
┌─────────────────────────▼────────────────────────────┐
│                  application 层                       │
│   facade/       门面（跨域聚合编排）                    │
│   service/      应用服务（用例）                        │
│   service/bo/   业务对象（跨层传输）                    │
│   service/mapstruct/ 对象映射（MapStruct）             │
└─────────────────────────┬────────────────────────────┘
                          │
┌─────────────────────────▼────────────────────────────┐
│                    domain 层                          │
│   {domain}/          领域包（如 user, tenant 等）      │
│   {domain}/entity/   领域实体                          │
│   {domain}/po/       持久化对象（@Table）               │
│   {domain}/repository/ 仓储（基于 R2dbcEntityTemplate）│
│   {domain}/XXAggregation  聚合根                      │
└─────────────────────────┬────────────────────────────┘
                          │
┌─────────────────────────▼────────────────────────────┐
│                infrastructure 层                      │
│   config/       配置（DB, 路由, 序列化等）              │
│   constants/    常量定义                               │
│   context/      Spring 上下文工具                      │
│   domain/       基础实体 / 领域事件 / PO 基类           │
│   filter/       WebFlux 过滤器                         │
│   helper/       工具帮助类（R2DBC, Reactor, Reflect）  │
│   properties/   全局配置属性                            │
│   utils/        工具类（JSON, ID, 加密, 版本等）         │
│   websocket/    WebSocket 配置（配置下发通道）           │
└──────────────────────────────────────────────────────┘
```

---

## 3. API 路由体系

通过自定义注解 + `WebFluxConfigurer.addPathPrefix()` 统一管理路径前缀，**不使用** `server.servlet.context-path`。

| 注解 | 路径前缀 | 用途 |
|------|----------|------|
| `@RequestMappingApiV1` | `/api/lazyday/v1` | 内部业务 API v1 |
| `@RequestMappingApiV2` | `/api/lazyday/v2` | 内部业务 API v2 |
| `@RequestMappingOpenV1` | `/api/open/v1` | 对外开放 API v1 |
| `@RequestMappingAdminV1` | `/api/admin/v1` | 管理后台 API |
| `@RequestMappingPortalV1` | `/api/portal/v1` | 开发者控制台 API |

---

## 4. 数据库设计规范

### 4.1 表命名
- 所有表以 `t_` 前缀
- 例：`t_user`, `t_tenant`, `t_app_key`, `t_call_log`

### 4.2 基础字段（继承 BaseAllUserTime）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 自增主键（GENERATED ALWAYS AS IDENTITY） |
| `create_user` | BIGINT | 创建人（关联 t_user.id） |
| `create_time` | timestamptz | 创建时间 |
| `update_user` | BIGINT | 更新人（关联 t_user.id） |
| `update_time` | timestamptz | 更新时间 |
| `deleted` | smallint | 软删除标记（0=正常, 1=删除） |

### 4.3 Flyway 迁移策略
- 通用脚本：`classpath:db/migration/`
- 数据库方言脚本：`classpath:db/migration-postgresql/`
- 脚本命名：`V{版本}__{描述}.sql`，如 `V1__init_tenant.sql`

---

## 5. 业务域

### 5.1 租户域（Tenant）

```
domain/tenant/
  ├── TenantAggregation.java      聚合根
  ├── entity/TenantEntity.java    领域实体
  ├── po/Tenant.java              持久化对象
  └── repository/TenantRepository.java  仓储
```

**核心字段**：
- `id` BIGINT 自增主键
- `name` 租户名称
- `code` 租户编码（唯一，用于路由标识）
- `email` 联系邮箱（唯一，用于登录）
- `status` 状态（active / suspended / deleted）
- `plan_id` BIGINT 套餐 ID（关联配额）

> **租户与用户关系**：租户注册时**同步创建** `t_user` 记录，租户管理员即该租户下的第一个用户。`t_tenant` 表独立管理租户级属性（套餐、状态），`t_user` 表管理账号认证（密码、角色）。一个租户可拥有多个用户（多成员协作，预留）。

### 5.2 AppKey 域

```
domain/appkey/
  ├── AppKeyAggregation.java
  ├── entity/AppKeyEntity.java
  ├── po/AppKey.java
  └── repository/AppKeyRepository.java
```

**核心字段**：
- `id` BIGINT 自增主键
- `tenant_id` BIGINT 所属租户
- `app_key` 32位随机字符串（唯一索引）
- `secret_key` 64位随机字符串（AES 加密存储）
- `status` 状态（active / disabled）
- `description` 描述
- `expire_time` 过期时间（可选）
- `last_used_time` 最后使用时间

> **加密密钥来源**：`secret_key` 使用 AES-256 加密存储，加密密钥从环境变量 `APPKEY_AES_KEY` 读取（配置于 `fan.service.appkey-aes-key`）。

### 5.3 配额域（Quota）

```
domain/quota/
  ├── QuotaAggregation.java
  ├── entity/QuotaEntity.java
  ├── po/QuotaPlan.java          配额套餐模板
  ├── po/TenantQuota.java        租户实际配额
  └── repository/QuotaRepository.java
```

**QuotaPlan 核心字段**：
- `id` BIGINT 自增主键
- `name` 套餐名称（如 Free / Pro / Enterprise）
- `qps_limit` 每秒请求数限制
- `monthly_limit` 月调用次数上限（-1 表示不限）
- `daily_limit` 日调用次数上限

### 5.4 调用日志域（CallLog）

```
domain/calllog/
  ├── po/CallLog.java
  └── repository/CallLogRepository.java
```

**核心字段**（高写入量，建议按月分区）：
- `id` BIGINT（雪花 ID，应用层生成）
- `tenant_id` BIGINT 租户 ID
- `app_key` 使用的 AppKey（VARCHAR）
- `path` 请求路径
- `method` HTTP 方法
- `status_code` 响应状态码
- `latency_ms` 响应耗时（毫秒）
- `request_time` 请求时间
- `ip` 来源 IP
- `error_msg` 错误信息（可选）

> **ID 生成策略**：调用日志表使用 **雪花算法**（应用层生成），避免数据库自增锁竞争，支持高并发写入。业务表（租户、AppKey 等）使用 **数据库自增序列**（PostgreSQL `GENERATED ALWAYS AS IDENTITY` 或 `SERIAL`），简化实现。

### 5.5 Webhook 域

```
domain/webhook/
  ├── WebhookAggregation.java
  ├── entity/WebhookEntity.java
  ├── po/WebhookConfig.java       Webhook 配置
  ├── po/WebhookEvent.java        待推送事件
  └── repository/WebhookRepository.java
```

**WebhookConfig 核心字段**：
- `id` BIGINT 自增主键
- `tenant_id` BIGINT 租户 ID
- `url` 回调地址
- `events` 订阅事件列表（JSON 数组）
- `secret` 签名密钥
- `status` 状态（active / disabled）
- `retry_count` 最大重试次数

**领域事件使用场景**：

| 事件 | 触发源 | 消费者 |
|------|--------|--------|
| `AppKeyDisabledEvent` | AppKey 被禁用 | Webhook 推送服务 |
| `TenantSuspendedEvent` | 租户被暂停 | Webhook 推送服务 |
| `QuotaWarningEvent` | 配额使用超 80% | Webhook 推送服务 |
| `QuotaExceededEvent` | 配额耗尽 | Webhook 推送服务 |

事件通过 `DomainEventPublisher` 发布，Webhook 服务监听后异步推送至租户配置的回调地址。

---

### 5.6 RAG 配置域

```
domain/rag/
  ├── RagConfigAggregation.java
  ├── entity/RagConfigEntity.java
  ├── po/RagConfig.java           RAG 知识库配置
  ├── po/RagDocument.java         知识库文档
  └── repository/RagRepository.java
```

**RagConfig 核心字段**：
- `id` BIGINT 自增主键
- `tenant_id` BIGINT 租户 ID
- `name` 知识库名称
- `description` 描述
- `embedding_model` 向量化模型（如 text-embedding-v4）
- `vector_dimension` 向量维度（默认 1536）
- `top_k` 检索 Top-K（默认 5）
- `similarity_threshold` 相似度阈值（默认 0.7）
- `status` 状态（active / disabled）
- `deleted` 软删除标记

**RagDocument 核心字段**：
- `id` BIGINT 自增主键
- `rag_config_id` BIGINT 所属知识库
- `title` 文档标题
- `content` 文档内容（或存储路径）
- `file_path` 文件存储路径（PDF/Word/TXT/Markdown）
- `embedding_status` 向量化状态（pending / processing / completed / failed）
- `chunk_size` 分块大小
- `chunk_overlap` 分块重叠

> **向量化流程**：文档上传后，状态置为 `pending`；后台任务分块处理后，调用 DashScope embedding 接口向量化，状态更新为 `processing`；完成后状态置为 `completed`；任一步骤失败置为 `failed`。

---

### 5.7 Agent 配置域

```
domain/agent/
  ├── AgentConfigAggregation.java
  ├── entity/AgentConfigEntity.java
  ├── po/AgentConfig.java         Agent 配置
  └── repository/AgentRepository.java
```

**AgentConfig 核心字段**：
- `id` BIGINT 自增主键
- `tenant_id` BIGINT 租户 ID
- `name` Agent 名称
- `system_prompt` 系统提示词
- `model` LLM 模型（如 qwen3.6-max-preview）
- `temperature` 温度参数
- `max_tokens` 最大 Token 数
- `tools` 绑定工具列表（JSON 数组，工具名称列表）
- `tts_config` TTS 配置（JSON）
  - `enabled` 是否启用
  - `voice` 音色（xiaoyun / xiaogang 等）
  - `speed` 语速（0.5 ~ 2.0）
  - `pitch` 音调（0.5 ~ 2.0）
  - `volume` 音量（0.1 ~ 2.0）
- `asr_config` ASR 配置（JSON）
  - `enabled` 是否启用
  - `language` 识别语言（zh-CN / en-US 等）
  - `model` ASR 模型（如 paraformer-realtime-v2）
  - `enable_punctuation` 标点开关
  - `enable_intermediate_result` 中间结果开关
- `rag_config_id` BIGINT 关联 RAG 知识库 ID（可选）
- `status` 状态（active / disabled）
- `deleted` 软删除标记

> **工具绑定说明**：`tools` 字段存储工具名称列表（如 `["search", "calculator"]`），Edge 大脑根据工具名称从本地 Tool 注册表加载对应实现，不在配置中存储工具代码。

---

### 5.8 Workflow 配置域

```
domain/workflow/
  ├── WorkflowConfigAggregation.java
  ├── entity/WorkflowConfigEntity.java
  ├── po/WorkflowConfig.java      Workflow 配置
  ├── po/WorkflowNode.java        流程节点
  └── repository/WorkflowRepository.java
```

**WorkflowConfig 核心字段**：
- `id` BIGINT 自增主键
- `tenant_id` BIGINT 租户 ID
- `name` 流程名称
- `description` 描述
- `version` 版本号（乐观锁）
- `entry_node_id` BIGINT 入口节点 ID
- `status` 状态（active / disabled / draft）
- `deleted` 软删除标记

**WorkflowNode 核心字段**：
- `id` BIGINT 自增主键
- `workflow_id` BIGINT 所属流程
- `node_type` 节点类型（start / llm / rag / tool / condition / end / tts / asr）
- `name` 节点名称
- `config` 节点配置（JSON）
  - LLM 节点：model, temperature, system_prompt
  - RAG 节点：rag_config_id, top_k
  - Tool 节点：tool_name, parameters
  - TTS 节点：voice, speed, pitch, volume
  - ASR 节点：language, model
- `next_nodes` 下游节点 ID 列表（JSON 数组，支持分支）
- `position_x` / `position_y` 可视化位置坐标

---

### 5.9 配置下发域

```
domain/configdispatch/
  ├── BrainConfigAggregation.java
  ├── entity/ConfigDispatchEntity.java
  ├── po/TenantBrainConfig.java   租户大脑完整配置快照
  └── repository/ConfigDispatchRepository.java
```

**TenantBrainConfig 核心字段**：
- `tenant_id` BIGINT 租户 ID（唯一，关联 t_tenant.id）
- `rag_config` RAG 配置 JSON 快照
- `agent_config` Agent 配置 JSON 快照
- `workflow_config` Workflow 配置 JSON 快照
- `version` 配置版本号（乐观锁，每次修改递增）
- `dispatch_status` 下发状态（pending / dispatched / failed）
- `dispatch_time` 下发时间
- `edge_ack_time` Edge 确认时间
- `update_time` 更新时间

**下发流程**：
1. Frontend 修改 RAG/Agent/Workflow 配置 → 调用 Backend API 保存
2. Backend 组装完整 `TenantBrainConfig`（RAG + Agent + Workflow JSON 快照）→ 写入数据库，version++
3. Backend 通过 WebSocket 主动推送至 Edge：`CONFIG_UPDATE {tenantId, version, config}`
4. Edge 接收后校验版本号（version ≥ 本地缓存），更新本地缓存（L1 Caffeine + L2 Redis），发送 ACK
5. Backend 收到 ACK 后更新 `dispatch_status = dispatched` + `edge_ack_time`

> **WebSocket 连接初始化**：Edge 启动时建立到 Backend 的 WebSocket 长连接（`/ws/edge`），注册自身 instanceId。Backend 持有连接会话，按 tenantId 路由消息。连接断开时，Backend 标记该 Edge 实例不可用，新请求 fallback 到其他实例或返回错误。

> **配置粒度说明**：当前 `t_tenant_brain_config` 以租户维度整合 RAG/Agent/Workflow 为一条记录（JSON 快照）。优势是下发简单、Edge 侧只需一次缓存查找；劣势是修改单个 Agent 需要重写整行、乐观锁 version 在跨配置类型修改时可能冲突。如后续租户配置数量增长到需要细粒度下发追踪（如上百个 Agent），可考虑拆分为 `config_type`（rag/agent/workflow）+ `config_id` 的组合主键方案。当前规模下整合方案已足够。

---

## 6. 响应式编程规范

### 6.1 返回类型
- 单个对象：`Mono<T>`
- 集合：`Flux<T>`
- 无返回值：`Mono<Void>`

### 6.2 错误处理
```java
// 统一使用 onErrorResume / onErrorMap 转换异常
return someService.doSomething()
    .onErrorMap(e -> new BusinessException("操作失败", e))
    .switchIfEmpty(Mono.error(new NotFoundException("资源不存在")));
```

### 6.3 上下文传递
通过 Reactor Context 传递用户信息（租户 ID、用户 ID），替代 ThreadLocal：
```java
Mono.deferContextual(ctx -> {
    String tenantId = ctx.get("tenantId");
    // ...
});
```

---

## 7. 加密规范

| 场景 | 算法 | 说明 |
|------|------|------|
| AppKey 请求签名 | HMAC-SHA256 | Edge 层验签 |
| SecretKey 存储 | AES-256 | 数据库加密存储 |
| 敏感数据传输 | RSA | 公钥加密、私钥解密 |
| 回调签名 | HMAC-SHA256 | Webhook 推送校验 |

---

## 8. 配置项规范

在 `application.yaml` 的 `fan.service` 下扩展：

```yaml
fan:
  service:
    domain-host: http://127.0.0.1:${server.port}
    context-path-v1: /api/lazyday/v1
    context-path-v2: /api/lazyday/v2
    open-context-path-v1: /api/open/v1
    admin-context-path-v1: /api/admin/v1
    portal-context-path-v1: /api/portal/v1
    access-key: lazyday-access-key
    secret-key: lazyday-secret-key
    appkey-aes-key: ${AES_KEY}                # 从环境变量读取
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat-model: qwen3.6-max-preview
      embedding-model: text-embedding-v4
      embedding-dimension: 1536
  tts:
    enabled: true
    voice: xiaoyun
    speed: 1.0
    pitch: 1.0
    volume: 1.0
  asr:
    enabled: true
    language: zh-CN
    model: paraformer-realtime-v2
```

---

## 9. WebSocket 配置（配置下发通道）

Edge 大脑通过 WebSocket 与 Backend 中枢保持长连接，接收配置下发。

> **注意**：项目基于 Spring WebFlux，必须使用 WebFlux 原生 WebSocket API（`WebSocketHandler`），**不得**使用 JSR 356（`javax.websocket`）阻塞式 API。

```java
@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketMapping(EdgeWebSocketHandler handler) {
        Map<String, WebSocketHandler> map = Map.of("/ws/edge", handler);
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(-1);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}

@Component
public class EdgeWebSocketHandler implements WebSocketHandler {

    private final EdgeRegistry edgeRegistry;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // 注册 Edge 实例
        edgeRegistry.register(session);

        // 接收 Edge 消息（ACK、心跳等）
        Mono<Void> input = session.receive()
            .map(WebSocketMessage::getPayloadAsText)
            .doOnNext(this::handleMessage)
            .then();

        // 返回合并的输入/输出流
        return input;
    }
}
```

Backend 保存配置后，通过会话推送消息：
```java
WebSocketSession edgeSession = edgeRegistry.getSession(tenantId);
if (edgeSession != null && edgeSession.isOpen()) {
    edgeSession.send(Mono.just(edgeSession.textMessage(jsonPayload))).subscribe();
}
```