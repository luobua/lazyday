# Edge 网关技术架构（大脑执行层）

> 版本：v0.5
> 日期：2026-04-25
> 作者：fanqibu

---

## 修订记录

| 版本 | 日期 | 修订内容 |
|------|------|---------|
| v0.5 | 2026-04-25 | §11 WebSocket 客户端改为 Reactor Netty ReactorNettyWebSocketClient；§12 错误响应格式统一为数字 code + 字符串 error_code；§8.3 调用日志增加 buffer 批量推送 + 本地降级；§10.5 新增会话上下文管理章节 |
| v0.4 | 2026-04-25 | **L-3**：补充所有 Edge 实例均断开时的 fallback 行为——返回 `BRAIN_NOT_READY`（HTTP 503），不降级处理；引入「大脑定位」章节，明确 Edge 为大脑执行层；§10 大脑组装重写，新增 Tool 注册表、缓存 stale-while-revalidate 降级行为、NPE 防御；新增 §11 WebSocket 客户端；tts_config 字段对齐（pitch/volume） |
| v0.3 | 2026-04-25 | 新增大脑组装（Brain Assembly）核心章节 |
| v0.2 | 2026-04-25 | 补充限流、熔断、日志采集详细设计 |
| v0.1 | 2026-04-25 | 初始版本 |

---

## 0. 大脑定位

Edge 是**大脑（Brain）的执行层**，在整个架构中的角色如下：

```
┌─────────────────────────────────────────────────────────────┐
│                        中枢（Central）                        │
│                                                             │
│   Frontend（管控台）  ←→  Backend（核心服务）  ←→  Edge（大脑） │
│                                                             │
│   · 管理、监控、调度                                         │
│   · 通过 WebSocket 下发大脑配置                             │
└─────────────────────────────────────────────────────────────┘

Edge 大脑的职责边界：
- ✅ 接收并缓存中枢下发的 RAG/Agent/Workflow 配置
- ✅ 组装 BrainContext（检索器+推理机+编排引擎+TTS/ASR）
- ✅ 执行 AI 请求（RAG 检索、LLM 对话、Workflow 编排、TTS/ASR）
- ❌ 不存储配置（无持久化）
- ❌ 不做业务逻辑（租户管理、配额管理等由中枢负责）
```

---

## 1. 定位与职责

Edge Service 是整个开放平台的**流量入口 + 大脑执行单元**，承担：

| 职责 | 说明 |
|------|------|
| **路由** | 将请求转发到 Backend，中枢管理类走 HTTP，AI 执行类走大脑 |
| **鉴权** | 验证 AppKey + 请求签名，确保合法调用 |
| **限流** | 按租户/接口粒度控制 QPS 和月配额 |
| **日志** | 每次调用记录链路信息，写入调用日志（推送至中枢） |
| **熔断** | 后端不可用时快速失败，防止级联故障 |
| **跨域** | 统一处理 CORS，Frontend 跨域访问 |
| **租户注入** | 鉴权成功后将 `X-Tenant-Id` 注入请求头传给 Backend |
| **🔥 大脑组装** | 接收中枢下发的 RAG/Agent/Workflow 配置，组装 AI 大脑处理 Open API 请求 |

---

## 2. 技术选型

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Cloud Gateway | 4.x | 基于 WebFlux 的响应式网关 |
| Spring Boot | 4.0.6 | 与 Backend 保持一致 |
| Redis | 7.x | 限流令牌桶、AppKey 缓存 |
| Bucket4j | 8.x | Redis 集成限流库 |
| Resilience4j | 2.x | 熔断降级 |
| Caffeine | - | 大脑配置 L1 本地缓存 |
| Micrometer | - | 指标采集，接入 Prometheus |

---

## 3. 项目结构

```
edge/
├── src/main/java/com/fan/lazyday/edge/
│   ├── EdgeApplication.java
│   ├── config/
│   │   ├── GatewayRouteConfig.java       路由配置
│   │   ├── RedisConfig.java              Redis 配置
│   │   ├── CaffeineConfig.java           Caffeine 配置（L1 缓存）
│   │   └── SecurityConfig.java          安全配置
│   ├── filter/
│   │   ├── AuthGlobalFilter.java         鉴权全局过滤器（AppKey + HMAC）
│   │   ├── RateLimitGlobalFilter.java    限流全局过滤器
│   │   ├── CallLogGlobalFilter.java      调用日志过滤器
│   │   └── TenantContextFilter.java      租户上下文注入
│   ├── service/
│   │   ├── AppKeyValidateService.java    AppKey 验证服务
│   │   ├── RateLimitService.java         限流服务
│   │   ├── CallLogService.java           调用日志写入服务
│   │   ├── BrainAssemblyService.java     🔥 大脑组装服务（核心）
│   │   └── ToolRegistry.java             🔥 工具注册表（Edge 本地）
│   ├── client/
│   │   └── BackendClient.java            调用 Backend HTTP Client
│   ├── model/
│   │   ├── AppKeyInfo.java               AppKey 缓存模型
│   │   ├── TenantQuotaInfo.java          租户配额信息
│   │   └── BrainContext.java             🔥 大脑上下文（组装结果）
│   ├── websocket/
│   │   ├── EdgeWebSocketClient.java      WebSocket 客户端（连接中枢 Backend）
│   │   └── ConfigMessageHandler.java      接收中枢配置消息
│   └── exception/
│       ├── AuthException.java
│       └── RateLimitException.java
└── src/main/resources/
    └── application.yaml
```

---

## 4. 请求处理流程

```
Request
   │
   ▼
┌─────────────────────────────────────────┐
│         TenantContextFilter              │  解析基础信息（路径/IP/时间戳）
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│           AuthGlobalFilter               │
│                                         │
│  1. 读取 Header: X-App-Key             │
│  2. 读 Redis 缓存 → 缓存未命中则异步查 Backend│
│     （通过 WebClient 非阻塞调用 /internal/appkey/validate）│
│  3. 验证 HMAC-SHA256 签名              │
│  4. 检查 AppKey 状态（active?）        │
│  5. 注入 X-Tenant-Id 到请求头           │
└──────────────────┬──────────────────────┘
                   │ 鉴权通过
                   ▼
┌─────────────────────────────────────────┐
│         RateLimitGlobalFilter            │
│                                         │
│  1. 读取租户配额（Redis 缓存）          │
│  2. QPS 检查（令牌桶算法）              │
│  3. 日配额检查（Redis 计数器）          │
│  4. 月配额检查（Redis 计数器）          │
└──────────────────┬──────────────────────┘
                   │ 配额通过
                   ▼
         ┌────────┴────────┐
         │  路由判断         │
         │                  │
         │ /api/open/v1/ai/*│ → 大脑组装 → 执行 AI 请求
         │ 其他路径          │ → HTTP 转发至 Backend
         └────────┬────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│         CallLogGlobalFilter              │
│  （doFinally — 请求完成后异步写日志）      │
└─────────────────────────────────────────┘
```

---

## 5. 鉴权设计

### 5.1 请求签名规范

第三方调用 Open API 时，须在请求头携带：

```http
X-App-Key: {appKey}
X-Timestamp: {unix毫秒时间戳}
X-Nonce: {随机字符串，防重放}
X-Sign: {HMAC-SHA256签名}
```

**签名算法**：
```
待签字符串 = HTTP方法 + "\n"
           + 请求路径 + "\n"
           + X-Timestamp + "\n"
           + X-Nonce + "\n"
           + RequestBody（GET请求为空字符串）

X-Sign = Base64(HMAC-SHA256(待签字符串, SecretKey))
```

**防重放**：
- `X-Timestamp` 与服务器时间相差超过 5 分钟则拒绝
- `X-Nonce` 写入 Redis，有效期 10 分钟，重复则拒绝

### 5.2 AppKey 缓存策略

```
查询 AppKey 信息流程：
1. 读 Redis key: appkey:{appKey}
2. 命中 → 直接使用，TTL 5分钟滚动
3. 未命中 → 调 Backend /internal/appkey/validate
4. 写入 Redis，TTL 5分钟
5. Backend 主动失效时推送 Redis Delete 事件
```

---

## 6. 限流设计

### 6.1 限流维度

| 维度 | Key 格式 | 算法 |
|------|----------|------|
| 租户 QPS | `ratelimit:qps:{tenantId}:{apiPath}` | 令牌桶（Bucket4j + Redis） |
| 租户日配额 | `quota:daily:{tenantId}:{date}` | Redis 计数器 + Lua 原子操作 |
| 租户月配额 | `quota:monthly:{tenantId}:{yearMonth}` | Redis 计数器 + Lua 原子操作 |

### 6.2 限流响应

```json
HTTP 429 Too Many Requests

{
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "请求频率超出限制",
  "retryAfter": 1000
}
```

响应头：
```
X-RateLimit-Limit: 100              # 配额上限（次/秒）
X-RateLimit-Remaining: 0            # 剩余可用次数
X-RateLimit-Reset: 1714061234567    # 配额重置时间（Unix 毫秒时间戳）
Retry-After: 1                      # 建议等待秒数（整数）
```

> **单位说明**：`X-RateLimit-Reset` 为 Unix 毫秒时间戳，`Retry-After` 为秒数，两者单位不同但语义明确，符合 RFC 规范。

---

## 7. 熔断降级

使用 **Resilience4j CircuitBreaker** + Spring Cloud Gateway 集成：

```yaml
resilience4j:
  circuitbreaker:
    instances:
      backend:
        slidingWindowType: TIME_BASED
        slidingWindowSize: 60           # 1 分钟时间窗口
        minimumNumberOfCalls: 20        # 至少 20 次调用才触发统计
        failureRateThreshold: 50        # 失败率超过 50% 触发熔断
        waitDurationInOpenState: 30s    # 熔断后等待 30s 进入半开
        permittedNumberOfCallsInHalfOpenState: 5
        slowCallDurationThreshold: 2s  # 超过 2s 视为慢调用
        slowCallRateThreshold: 80      # 慢调用率超过 80% 触发熔断
```

降级响应：
```json
HTTP 503 Service Unavailable

{
  "code": "SERVICE_UNAVAILABLE",
  "message": "服务暂时不可用，请稍后重试"
}
```

---

## 8. 调用日志

### 8.1 日志采集

在 `CallLogGlobalFilter` 的 `doFinally` 中**异步**写入，不阻塞响应：

```java
// 响应完成后异步发送到中枢 Backend
.doFinally(signal -> {
    // 推送至中枢 Backend（不等待响应）
    backendClient.pushCallLog(callLog).subscribe();
});
```

### 8.2 日志内容

| 字段 | 来源 |
|------|------|
| `tenant_id` | Auth 过滤器注入 |
| `app_key` | 请求头 X-App-Key |
| `path` | 请求路径 |
| `method` | HTTP 方法 |
| `status_code` | 响应状态码 |
| `latency_ms` | 请求开始到响应结束时间差 |
| `request_time` | 请求时间戳 |
| `client_ip` | X-Forwarded-For / RemoteAddress |

### 8.3 写入策略

Edge 将调用日志通过 WebSocket **批量**推送至中枢 Backend，Backend 批量写入 PostgreSQL：

```java
// Edge 侧：buffer 批量推送至中枢（100 条或 1 秒，先到先发）
private final Sinks.Many<CallLog> logSink = Sinks.many().unicast().onBackpressureBuffer();

// 请求完成后投递到 sink
.doFinally(signal -> {
    logSink.tryEmitNext(callLog);
});

// 初始化时订阅 sink，批量推送
@PostConstruct
public void initLogPush() {
    logSink.asFlux()
        .bufferTimeout(100, Duration.ofSeconds(1))
        .flatMap(batch -> websocketClient.pushCallLogs(batch)
            .onErrorResume(e -> {
                // 推送失败时写入本地文件，后台重传
                localLogBuffer.append(batch);
                return Mono.empty();
            }))
        .subscribe();
}

// Backend 侧：WebSocket 接收 → 批量写库
@MessageMapping("/calllog")
public Mono<Void> receiveCallLog(List<CallLog> logs) {
    return callLogRepository.batchInsert(logs);
}
```

> **降级策略**：WebSocket 缓冲区满或连接断开时，日志暂存本地文件（`/tmp/edge-logs/`），重连后异步重传。避免高并发下日志丢失。

---

## 9. 路由配置

```yaml
spring:
  cloud:
    gateway:
      routes:
        # Open API — AI 大脑执行路径
        - id: open-api-ai
          uri: forward://brain          # 本地大脑执行（不走 HTTP）
          predicates:
            - Path=/api/open/v1/ai/**
          filters:
            - AuthFilter
            - RateLimitFilter
            - CallLogFilter

        # Open API — 非 AI 业务转发
        - id: open-api-backend
          uri: http://backend:8081
          predicates:
            - Path=/api/open/v1/manage/**
          filters:
            - AuthFilter
            - RateLimitFilter
            - CallLogFilter

        # Portal API 路由（JWT 鉴权，不经过 AppKey 鉴权过滤器）
        - id: portal-api
          uri: http://backend:8081
          predicates:
            - Path=/api/portal/v1/**
          filters:
            - JwtAuthFilter
            - CallLogFilter

        # Admin API 路由（Admin JWT 鉴权）
        - id: admin-api
          uri: http://backend:8081
          predicates:
            - Path=/api/admin/v1/**
          filters:
            - JwtAuthFilter
            - AdminRoleFilter
            - CallLogFilter
```

---

## 10. 大脑组装（Brain Assembly）

### 10.1 核心定位

大脑组装是 Edge 的**核心差异化能力**。Edge 不存储配置，只执行配置。中枢（Backend）通过 WebSocket 将 RAG/Agent/Workflow 配置下发至 Edge，Edge 在内存中组装 BrainContext，处理请求后不持久化任何状态。

### 10.2 配置同步机制

```
Backend 中枢 ──WebSocket──► Edge 大脑
    │                          │
    │  CONFIG_UPDATE           │  1. 接收配置消息
    │  {tenantId, version,      │  2. 校验版本号（递增）
    │   rag, agent,            │  3. 更新本地缓存（L1 Caffeine + L2 Redis）
    │   workflow}              │  4. 发送 ACK 确认
    │                          │
    │◄── ACK ──────────────────│
    │  {instanceId, version}
```

**多级缓存策略**：

| 层级 | 存储 | TTL | 用途 |
|------|------|-----|------|
| L1 | Caffeine（Edge 本地内存） | 10 分钟 | 热配置，常驻内存，零网络开销 |
| L2 | Redis（`brain:config:{tenantId}`） | 1 小时 | 跨实例共享，Edge 重启后快速预热 |
| L3 | 中枢 Backend | 持久化 | 配置唯一来源，缓存 miss 时拉取 |

**缓存降级行为**：
- L1 miss → 查 L2 Redis → L2 miss → 通过 WebSocket 向中枢请求同步
- 配置同步期间（stale-while-revalidate）：允许使用过期配置处理请求，同时后台更新缓存
- WebSocket 断开时：L1/L2 缓存继续服务，重连后增量同步
- **所有 Edge 实例均断开时**（edgeRegistry.get(tenantId) 返回空）：Edge 返回 `BRAIN_NOT_READY`（HTTP 503），请求不降级处理，避免脏数据风险；Backend 侧收到首个重新注册的 Edge 实例后，自动推送最新配置

### 10.3 工具注册表

Edge 本地维护一个 **Tool Registry**（工具名称 → 实现类的映射），配置中的 `tools` 字段只存储工具名称列表：

```java
@Component
public class ToolRegistry {

    private final Map<String, ToolExecutor> tools = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 从 classpath 扫描 ToolExecutor 实现类并注册
        tools.put("search", new SearchToolExecutor());
        tools.put("calculator", new CalculatorToolExecutor());
        tools.put("code_runner", new CodeRunnerToolExecutor());
        // ... 更多内置工具
    }

    public ToolExecutor get(String toolName) {
        return tools.get(toolName);
    }

    public List<String> list() {
        return new ArrayList<>(tools.keySet());
    }
}
```

> **工具来源**：Edge 的工具以插件形式注册（实现 `ToolExecutor` 接口，打包到 Edge 应用中）。配置中的 `tools: ["search", "calculator"]` 是工具名称列表，BrainAssemblyService 根据名称从注册表加载实现。

### 10.4 大脑组装流程

```java
@Component
public class BrainAssemblyService {

    private final CaffeineCache<String, BrainContext> l1Cache;  // Caffeine L1
    private final RedisTemplate<String, BrainContext> l2Cache;  // Redis L2
    private final ToolRegistry toolRegistry;

    public Mono<BrainContext> assembleBrain(String tenantId, String requestType) {
        return getTenantConfig(tenantId)
            .map(config -> {
                BrainContext brain = new BrainContext();

                // 1. 组装 RAG 检索器
                if (config.getRagConfig() != null) {
                    RagConfig rag = config.getRagConfig();
                    brain.setRetriever(new PgVectorRetriever(
                        rag.getEmbeddingModel(),
                        rag.getTopK(),
                        rag.getSimilarityThreshold()
                    ));
                }

                // 2. 组装 Agent（只有 Agent 配置存在时才组装）
                if (config.getAgentConfig() != null) {
                    AgentConfig agent = config.getAgentConfig();
                    List<ToolExecutor> boundTools = Optional.ofNullable(agent.getTools())
                        .orElse(Collections.emptyList())
                        .stream()
                        .map(toolRegistry::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                    brain.setAgent(new AIAgent(
                        agent.getModel(),
                        agent.getSystemPrompt(),
                        agent.getTemperature(),
                        agent.getMaxTokens(),
                        boundTools
                    ));

                    // 3. 组装 TTS（需要 Agent 配置存在且 TTS 启用）
                    if (agent.getTtsConfig() != null && agent.getTtsConfig().isEnabled()) {
                        brain.setTtsClient(new DashScopeTtsClient(
                            agent.getTtsConfig().getVoice(),
                            agent.getTtsConfig().getSpeed(),
                            agent.getTtsConfig().getPitch(),
                            agent.getTtsConfig().getVolume()
                        ));
                    }

                    // 4. 组装 ASR（需要 Agent 配置存在且 ASR 启用）
                    if (agent.getAsrConfig() != null && agent.getAsrConfig().isEnabled()) {
                        brain.setAsrClient(new DashScopeAsrClient(
                            agent.getAsrConfig().getLanguage(),
                            agent.getAsrConfig().getModel()
                        ));
                    }
                }

                // 5. 组装 Workflow 执行器（只有 Workflow 配置存在时才组装）
                if (config.getWorkflowConfig() != null) {
                    WorkflowConfig wf = config.getWorkflowConfig();
                    brain.setWorkflowEngine(new WorkflowEngine(
                        wf.getNodes(),
                        wf.getEntryNodeId(),
                        this::assembleBrain  // 节点执行时可能递归组装子大脑
                    ));
                }

                return brain;
            })
            .cache();  // 同一请求内复用 BrainContext
    }
}
```

### 10.5 会话上下文管理

多轮对话场景下，Edge 大脑需要在请求间维护对话上下文：

- **存储**：会话数据存入 Redis（`session:{sessionId}`），Edge 本地 Caffeine 做 L1 热缓存（TTL 5 分钟）
- **Edge 不持久化**：会话数据通过 Redis 跨实例共享，Edge 重启后从 Redis 恢复
- **上下文窗口**：保留最近 N 轮对话（默认 20 轮），超出后滑动窗口截断
- **会话隔离**：`session_id` 绑定 `tenant_id` + `agent_id`，防止跨租户访问

详见 requirements-design.md §10。

### 10.6 请求处理模式

根据 Open API 请求类型，选择不同的大脑处理模式：

| 请求类型 | 处理模式 | 说明 |
|----------|----------|------|
| `/api/open/v1/ai/chat` | Agent + RAG | LLM 对话，优先检索知识库增强回答 |
| `/api/open/v1/ai/tts` | TTS | 文本转语音 |
| `/api/open/v1/ai/asr` | ASR | 语音转文本 |
| `/api/open/v1/ai/workflow` | Workflow | 执行自定义流程编排 |
| `/api/open/v1/ai/agent` | Agent + RAG + TTS/ASR | 完整 AI Agent 交互（文本/语音多模式） |

### 10.7 TTS/ASR 配置动态切换

Agent 配置中的 TTS/ASR 参数在 Edge 大脑中**运行时动态生效**，无需重启 Edge：

```yaml
# AgentConfig.tts_config
{
  "enabled": true,
  "voice": "xiaoyun",
  "speed": 1.0,
  "pitch": 1.0,
  "volume": 1.0
}

# AgentConfig.asr_config
{
  "enabled": true,
  "language": "zh-CN",
  "model": "paraformer-realtime-v2",
  "enable_punctuation": true,
  "enable_intermediate_result": false
}
```

BrainAssemblyService 在每次组装 BrainContext 时，从配置中读取最新参数，实时创建/更新 TTS/ASR 客户端实例。

---

## 11. WebSocket 客户端（连接中枢）

Edge 启动时通过 Reactor Netty `WebSocketClient` 主动连接 Backend 中枢，接收配置下发。

> **注意**：Edge 基于 Spring Cloud Gateway（WebFlux），必须使用 Reactor Netty 的 `ReactorNettyWebSocketClient`，**不得**使用 JSR 356（`javax.websocket`）阻塞式 API。

```java
@Component
public class EdgeWebSocketClient {

    private final ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
    private final ConfigMessageHandler configMessageHandler;

    @PostConstruct
    public void connect() {
        doConnect().subscribe();
    }

    private Mono<Void> doConnect() {
        URI uri = URI.create("ws://backend:8081/ws/edge");
        return client.execute(uri, session -> {
            // 注册 Edge 实例
            Mono<Void> register = session.send(Mono.just(
                session.textMessage("{\"type\":\"REGISTER\",\"instanceId\":\"" + instanceId + "\"}")
            ));

            // 心跳保活（每 30s 发送 PING）
            Mono<Void> heartbeat = Flux.interval(Duration.ofSeconds(30))
                .flatMap(i -> session.send(Mono.just(session.pingMessage(buf -> buf))))
                .then();

            // 接收中枢配置消息
            Mono<Void> receive = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(msg -> {
                    ConfigMessage configMsg = JsonUtils.parse(msg, ConfigMessage.class);
                    if ("CONFIG_UPDATE".equals(configMsg.getType())) {
                        configMessageHandler.handleConfigUpdate(configMsg);
                    }
                })
                .then();

            return register.then(Mono.zip(heartbeat, receive).then());
        })
        .doOnError(e -> {
            // 重连指数退避：1s → 2s → 4s → 8s → 16s → 30s（最大）
            Mono.delay(Duration.ofMillis(computeBackoff()))
                .flatMap(i -> doConnect())
                .subscribe();
        });
    }
}
```

---

## 12. 统一错误响应格式

Edge 与 Backend 采用**统一响应格���**，`code` 为数字（0=成功，非零=失败），`error_code` 为机器可读的字符串标识：

```json
{
  "code": 40101,
  "error_code": "AUTH_FAILED",
  "message": "签名验证失败",
  "data": null,
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| code | error_code | HTTP 状态码 | 说明 |
|------|------------|-------------|------|
| 40101 | `AUTH_FAILED` | 401 | 鉴权失败 |
| 40102 | `APP_KEY_NOT_FOUND` | 401 | AppKey 不存在 |
| 40103 | `SIGN_EXPIRED` | 401 | 签名已过期 |
| 40104 | `SIGN_REPLAY` | 401 | 重放攻击 |
| 40301 | `APP_KEY_DISABLED` | 403 | AppKey 已禁用 |
| 42901 | `RATE_LIMIT_EXCEEDED` | 429 | QPS 超限 |
| 42902 | `QUOTA_EXCEEDED` | 429 | 配额耗尽 |
| 50301 | `SERVICE_UNAVAILABLE` | 503 | 后端不可用 |
| 50302 | `BRAIN_NOT_READY` | 503 | 大脑配置未就绪（未收到中枢下发） |

> **编码规则**：`code` 由 HTTP 状态码 + 两位序号组成（如 40101 = 401 + 01）。客户端可根据 `error_code` 字符串做程序化判断，根据 `code` 数字做分类处理。此格式与 Backend 通用响应格式一致（见 requirements-design.md §6.1）。