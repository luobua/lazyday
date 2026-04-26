# 需求设计文档

> 版本：v0.6
> 日期：2026-04-26
> 作者：fanqibu

---

## 修订记录

| 版本 | 日期 | 修订内容 |
|------|------|---------|
| v0.6 | 2026-04-26 | 新增 §5.5 t_tenant_quota 表（租户配额实例）；§7 邮件服务 Phase 标注从 Phase 2 修正为 Phase 3；§5 编号顺延（5.5→5.6, ..., 5.13→5.14） |
| v0.5 | 2026-04-25 | ID 策略统一为 BIGINT 自增序列（§5 全量 DDL 修改）；新增 §5.1 t_user 表；新增 §5.8 t_webhook_event 表；§5.3 t_app_key 增加 SecretKey 轮换字段；所有表补齐 create_user/update_user/deleted 审计字段；§4.1 性能 SLA 按接口类型拆分；§6.1 错误响应增加 error_code 字段；新增 §10 会话存储设计；新增 §11 SpEL 安全设计 |
| v0.4 | 2026-04-25 | **R-1**：修正 `POST /rag/{id}/search` 返回格式，补全标准外层包装 `{code, message, data.items}`；**R-2**：`t_tenant_brain_config` 补充 `create_user`、`update_user`、`deleted` 审计字段，与 `BaseAllUserTime` 规范对齐；**M-3**：`dispatch_status` 字段注释补充分值说明 `pending/dispatched/failed` |
| v0.3 | 2026-04-25 | 首次系统性更新，新增 AI 大脑（中枢+大脑）完整规划 |
| v0.2 | 2026-04-25 | 补充 Webhook、套餐管理、配额域详细设计 |
| v0.1 | 2026-04-25 | 初始版本 |

---

## 0. 中枢与大脑概念

本文档涉及**中枢**与**大脑**两个核心概念：

| 角色 | 包含组件 | 职责 |
|------|---------|------|
| **中枢（Central）** | Frontend（管理界面） + Backend（核心服务） | 管理、配置、监控、调度 AI 能力 |
| **大脑（Brain）** | Edge（执行单元） | 接收配置，执行 RAG/Agent/Workflow/TTS/ASR 推理 |

> 配置从**中枢**下发至**大脑**，**大脑**不存储配置，只执行配置。

---

## 1. 产品概述

Lazyday 开放平台是一个**多租户 API 开放接口平台**，允许外部开发者/企业以租户身份注册，获取 AppKey 后调用平台提供的开放接口，并通过开发者控制台进行自助管理。

### 1.1 用户角色

| 角色 | 说明 | 入口 |
|------|------|------|
| **平台管理员（Admin）** | 管理所有租户、配额、系统配置、大脑配置下发 | Admin Console `/admin` |
| **租户管理员（Tenant Admin）** | 管理本租户的 AppKey、RAG/Agent/Workflow 配置、查看调用统计 | Developer Portal `/portal` |
| **开放 API 调用方** | 第三方应用，通过 AppKey 调用 Open API（由 Edge 大脑执行） | API 直接调用 |

### 1.2 核心价值主张

- **租户自助**：注册即用，无需人工审批（或轻审批）
- **安全可控**：HMAC 签名鉴权，防重放攻击
- **透明可观测**：调用日志实时可查，配额用量清晰
- **Webhook 驱动**：关键事件实时推送，不依赖轮询
- **AI 大脑可配置**：租户可自定义 RAG/Agent/Workflow，大脑自动组装执行

---

## 2. 功能模块列表

### 2.1 总览

```
开放平台
├── 租户管理
│   ├── 租户注册 / 登录
│   ├── 套餐绑定
│   └── 租户状态管理（Admin 操作）
├── AppKey 管理
│   ├── 创建 / 查看 / 禁用 / 删除
│   ├── SecretKey 轮换
│   └── 权限范围配置（预留）
├── 配额管理
│   ├── 套餐模板（Admin 配置）
│   └── 租户配额实例
├── 调用日志
│   ├── 明细查询
│   └── 统计聚合（图表）
├── Webhook
│   ├── 配置管理
│   ├── 事件订阅
│   └── 推送重试
├── RAG 管理（中枢 — 配置层）
│   ├── 知识库 CRUD
│   ├── 文档上传 / 向量化
│   └── 检索参数配置
├── Agent 管理（中枢 — 配置层）
│   ├── Agent CRUD
│   ├── 系统提示词 / 模型参数
│   ├── 工具绑定
│   └── TTS / ASR 配置
├── Workflow 管理（中枢 — 配置层）
│   ├── Workflow CRUD
│   ├── 可视化流程编排
│   └── 版本管理
├── 配置下发（中枢 → 大脑）
│   └── RAG / Agent / Workflow 配置推送至 Edge 大脑
└── 开放 API（Edge 大脑 — 执行层）
    ├── AI 对话（chat）
    ├── 语音合成（tts）
    ├── 语音识别（asr）
    ├── Workflow 执行（workflow）
    └── Agent 交互（agent）
```

---

## 3. 详细功能需求

### 3.1 租户注册与认证

#### 3.1.1 租户注册

**触发方式**：用户在 Developer Portal 填写注册表单

**输入字段**：
- 租户名称（公司/个人名）
- 联系邮箱（唯一，用于登录）
- 密码（8位以上，含大小写字母+数字）
- 手机号（可选）

**流程**：
1. 输入邮箱、密码提交
2. ~~系统发送邮箱验证码~~（Phase 1 暂不实现邮箱验证，后续补充）
3. 系统自动创建租户并绑定 **Free 套餐**
4. 跳转到控制台首页

> **邮箱服务**：Phase 1 跳过邮箱验证码，租户注册后直接激活。Phase 2 引入邮件服务（JavaMail + SMTP 或阿里云邮件推送），补充邮箱验证、配额告警邮件等功能。

**业务规则**：
- 同一邮箱只能注册一个租户
- 租户初始状态 `active`
- 自动创建默认 AppKey 一个

#### 3.1.2 登录

- 邮箱 + 密码登录
- 登录成功颁发 JWT（Access Token 2h + Refresh Token 7d）
- 支持「记住我」（Refresh Token 延长至 30d）

#### 3.1.3 Admin 登录

- 独立 Admin 账号，不通过租户注册流程创建
- Admin 账号由系统初始化或超级 Admin 创建

---

### 3.2 AppKey 管理

#### 3.2.1 创建 AppKey

**入口**：Portal → 凭证管理 → 创建

**字段**：
- 名称/备注（用于区分多个 Key 的用途）
- 有效期（可选，默认永不过期）

**业务规则**：
- 每个租户最多创建 **5 个** AppKey（Free 套餐），Pro 套餐可无限创建
- 创建时生成：`appKey`（32位）+ `secretKey`（64位）
- `secretKey` **仅在创建时显示一次**，后续不可查看（数据库 AES 加密存储）
- 系统自动记录创建人、创建时间

**响应示例**：
```json
{
  "appKey": "ak_live_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "secretKey": "sk_live_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "createdAt": "2026-04-25 10:00:00",
  "warning": "请立即保存 SecretKey，离开此页面后将无法再次查看"
}
```

#### 3.2.2 禁用/启用 AppKey

- 禁用后，Edge 网关 5 分钟内（缓存 TTL）生效拒绝
- 禁用不删除，历史调用日志保留
- 可随时重新启用

#### 3.2.3 轮换 SecretKey

**场景**：SecretKey 疑似泄露时

**流程**：
1. 点击「轮换 SecretKey」
2. 系统生成新 SecretKey
3. 旧 SecretKey **宽限期 24 小时**内仍有效（两个 Secret 并存）
4. 24 小时后旧 SecretKey 自动失效
5. 宽限期内可手动提前终止旧 Key

#### 3.2.4 删除 AppKey

- 软删除，不物理删除
- 已删除的 AppKey 立即失效（不等缓存 TTL）
- 历史调用日志中的 appKey 字段保留（仅做文本记录）

---

### 3.3 配额管理

#### 3.3.1 套餐模板（Admin 配置）

| 套餐 | QPS | 日调用量 | 月调用量 | AppKey 上限 |
|------|-----|---------|---------|------------|
| Free | 5 | 1,000 | 10,000 | 5 |
| Pro | 50 | 50,000 | 500,000 | 不限 |
| Enterprise | 自定义 | 自定义 | 自定义 | 不限 |

**Admin 可动态配置套餐参数**，修改套餐后，已绑定租户的配额**实时生效**（Edge 缓存 1 分钟内同步）。

#### 3.3.2 租户配额实例

- 每个租户绑定一个套餐实例
- Admin 可**单独覆盖**某租户的配额（临时调整，不影响套餐模板）
- 配额重置周期：自然日（每日 00:00）和自然月（每月 1 日）

#### 3.3.3 配额告警（可选，v2 规划）

- 月配额使用超过 80% 时，发送邮件提醒租户
- 月配额耗尽时，发送邮件通知

---

### 3.4 调用日志

#### 3.4.1 明细查询

**Portal 视角**（租户只能看自己的）：

| 筛选项 | 说明 |
|--------|------|
| 时间范围 | 最近 1h / 24h / 7d / 30d / 自定义 |
| AppKey | 选择特定 AppKey |
| 接口路径 | 模糊匹配 |
| 状态码 | 全部 / 2xx / 4xx / 5xx |

**展示字段**：
- 请求时间
- AppKey（脱敏，显示前8位+...）
- 接口路径
- HTTP 方法
- 状态码（带颜色徽标）
- 耗时（ms）
- 来源 IP

支持**翻页**（默认每页 20 条）和 **CSV 导出**。

#### 3.4.2 统计聚合（图表）

Portal 首页展示：
- **调用量趋势图**：折线图，按天/小时聚合
- **成功率**：成功（2xx）vs 失败（4xx+5xx）
- **接口 Top 10**：按调用量排序
- **平均耗时趋势**

> **性能说明**：30 天查询通常只涉及 1-2 个月分区，性能可控（目标 ≤ 500ms）。超过 30 天的查询需要限制时间范围或走预聚合表（`t_call_log_daily_agg`）。

---

### 3.5 Webhook 管理

#### 3.5.1 创建 Webhook

**字段**：
- 回调 URL（HTTPS 必须）
- 监听事件（多选）
- 备注描述

**可订阅事件**：

| 事件 | 触发时机 |
|------|---------|
| `quota.warning` | 月配额使用超过 80% |
| `quota.exceeded` | 月配额耗尽 |
| `appkey.disabled` | AppKey 被禁用（含 Admin 操作） |
| `tenant.suspended` | 租户被暂停 |

#### 3.5.2 回调格式

```http
POST {回调URL}
Content-Type: application/json
X-Lazyday-Event: quota.warning
X-Lazyday-Timestamp: 1714061234567
X-Lazyday-Sign: {HMAC-SHA256签名}

{
  "event": "quota.warning",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1714061234567,
  "data": {
    "used": 8000,
    "limit": 10000,
    "percentage": 80
  }
}
```

**签名验证**：
```
X-Lazyday-Sign = Base64(HMAC-SHA256(X-Lazyday-Timestamp + "." + Body, WebhookSecret))
```

#### 3.5.3 推送重试

- 超时时间：10 秒
- 重试策略：指数退避，间隔 1min → 5min → 30min → 2h → 6h
- 最大重试次数：5 次
- 超过最大重试后，标记为「永久失败」，Portal 显示失败记录并可手动触发重推

#### 3.5.4 测试 Webhook

- 点击「发送测试事件」，向回调 URL 发送一条 `test` 事件
- 显示推送结果（HTTP 状态码 + 响应内容）

---

### 3.6 Admin 功能

#### 3.6.1 租户管理

- 租户列表（支持搜索：名称/邮箱/状态）
- 查看租户详情：基本信息 + 套餐信息 + 使用量 + AppKey 列表
- 操作：
  - 修改绑定套餐
  - 临时调整配额
  - 暂停/恢复租户
  - 手动重置月配额（特殊情况补偿）

#### 3.6.2 套餐管理

- 创建 / 编辑套餐（QPS、日限、月限、AppKey 上限）
- 查看套餐绑定租户数量
- 停用套餐（已绑定租户不受影响，仅禁止新绑定）
- 删除套餐（软删除，已绑定租户的 `plan_id` 不变，新租户不可选该套餐）

#### 3.6.3 系统概览大盘

- 总租户数 / 活跃租户数
- 今日全平台总调用量
- 今日成功率
- 各接口调用 Top 10
- 系统健康状态（Edge + Backend 状态检查）

---

## 4. 非功能性需求

### 4.1 性能

| 指标 | 目标值 | 说明 |
|------|--------|------|
| Edge 鉴权 + 限流处理时间 | ≤ 5ms | 网关层固定开销 |
| Open API 非 AI 接口 P99 延迟 | ≤ 200ms | 管理类 API（CRUD 转发） |
| Open API AI chat P99（非流式） | ≤ 10s | 受 LLM 推理耗时影响 |
| Open API AI chat TTFB（流式） | ≤ 2s | 首 token 响应时间 |
| Open API TTS/ASR P99 延迟 | ≤ 5s | 受音频处理耗时影响 |
| 控制台页面首屏加载 | ≤ 2s | Next.js SSR |
| 调用日志查询（最近 30 天） | ≤ 500ms | 分区表查询 |

### 4.2 可用性

- Backend + Edge 目标可用性：**99.9%**
- 数据库 PostgreSQL 主从热备

### 4.3 安全

- 所有外部接口走 HTTPS（TLS 1.2+）
- SecretKey 数据库 AES-256 加密存储
- 防 SQL 注入：使用参数化查询（R2DBC 已处理）
- XSS 防护：Next.js 默认 Content-Security-Policy
- CSRF 防护：SameSite Cookie + CSRF Token
- 登录失败超过 5 次，账号锁定 15 分钟

### 4.4 可观测性

- Edge + Backend 均接入结构化日志（Logback JSON 格式）
- Actuator 健康检查端点
- 核心指标接入 Prometheus（调用量、错误率、延迟分位数）
- 预留 Grafana 看板

---

## 5. 数据模型（核心表）

### 5.1 t_user

```sql
CREATE TABLE t_user (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,                -- 所属租户
    email           VARCHAR(200) NOT NULL,
    password_hash   VARCHAR(200) NOT NULL,           -- bcrypt 哈希
    nickname        VARCHAR(100),
    role            VARCHAR(20) NOT NULL DEFAULT 'tenant_admin',  -- tenant_admin / member / platform_admin
    status          VARCHAR(20) NOT NULL DEFAULT 'active',        -- active / disabled
    last_login_time TIMESTAMPTZ,
    create_user     BIGINT,
    create_time     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_user     BIGINT,
    update_time     TIMESTAMPTZ,
    deleted         SMALLINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_user_email ON t_user(email) WHERE deleted = 0;
CREATE INDEX idx_user_tenant ON t_user(tenant_id);
```

> **用户角色**：`tenant_admin` 为租户管理员（注册时自动创建）；`member` 为租户普通成员（预留多成员协作）；`platform_admin` 为平台管理员（Admin Console 登录）。Platform Admin 的 `tenant_id` 为 0（无租户归属）。

### 5.2 t_tenant

```sql
CREATE TABLE t_tenant (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(50) UNIQUE NOT NULL,
    email       VARCHAR(200) UNIQUE NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'active',
    plan_id     BIGINT,
    create_user BIGINT,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_user BIGINT,
    update_time TIMESTAMPTZ,
    deleted     SMALLINT NOT NULL DEFAULT 0
);
```

### 5.3 t_app_key

```sql
CREATE TABLE t_app_key (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id               BIGINT NOT NULL,
    app_key                 VARCHAR(64) UNIQUE NOT NULL,
    secret_key              TEXT NOT NULL,               -- AES 加密存储
    previous_secret_key     TEXT,                        -- 轮换时保留旧 Secret（AES 加密）
    rotation_deadline       TIMESTAMPTZ,                 -- 旧 Secret 宽限期截止时间（24h）
    name                    VARCHAR(100),
    status                  VARCHAR(20) NOT NULL DEFAULT 'active',
    expire_time             TIMESTAMPTZ,
    last_used_time          TIMESTAMPTZ,
    create_user             BIGINT,
    create_time             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_user             BIGINT,
    update_time             TIMESTAMPTZ,
    deleted                 SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_app_key_tenant ON t_app_key(tenant_id);
CREATE INDEX idx_app_key_key ON t_app_key(app_key) WHERE deleted = 0;
```

### 5.4 t_quota_plan

```sql
CREATE TABLE t_quota_plan (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(50) NOT NULL,
    qps_limit       INT NOT NULL DEFAULT 5,
    daily_limit     BIGINT NOT NULL DEFAULT 1000,
    monthly_limit   BIGINT NOT NULL DEFAULT 10000,
    max_app_keys    INT NOT NULL DEFAULT 5,     -- -1 表示不限
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    create_user     BIGINT,
    create_time     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_user     BIGINT,
    update_time     TIMESTAMPTZ,
    deleted         SMALLINT NOT NULL DEFAULT 0
);
```

> **套餐删除策略**：套餐支持**软删除**（`deleted = 1`），已绑定该套餐的租户**不受影响**（plan_id 不变），仅禁止新租户绑定该套餐。

### 5.5 t_tenant_quota

```sql
CREATE TABLE t_tenant_quota (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           BIGINT NOT NULL UNIQUE,     -- 每个租户一条配额实例
    plan_id             BIGINT NOT NULL,             -- 关联 t_quota_plan.id
    custom_qps_limit    INT,                         -- Admin 自定义覆盖（NULL 则使用套餐默认值）
    custom_daily_limit  BIGINT,                      -- Admin 自定义覆盖
    custom_monthly_limit BIGINT,                     -- Admin 自定义覆盖
    custom_max_app_keys INT,                         -- Admin 自定义覆盖
    create_user         BIGINT,
    create_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_user         BIGINT,
    update_time         TIMESTAMPTZ,
    deleted             SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_tenant_quota_tenant ON t_tenant_quota(tenant_id);
```

> **配额生效优先级**：`custom_*` 字段优先于 `t_quota_plan` 的默认值。Edge 限流时读取 Redis 缓存中的有效配额（Backend 在套餐/自定义配额变更时主动刷新 Redis 缓存）。Admin 可通过 `PUT /admin/v1/tenants/{id}/quota` 单独覆盖某租户的配额，不影响套餐模板。

### 5.6 t_call_log

```sql
CREATE TABLE t_call_log (
    id              BIGINT PRIMARY KEY,         -- 雪花 ID（应用层生成）
    tenant_id       BIGINT NOT NULL,
    app_key         VARCHAR(64) NOT NULL,
    path            VARCHAR(500) NOT NULL,
    method          VARCHAR(10) NOT NULL,
    status_code     SMALLINT NOT NULL,
    latency_ms      INT NOT NULL,
    client_ip       VARCHAR(50),
    error_msg       TEXT,
    request_time    TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (request_time);           -- 按月分区

-- 创建当月分区示例
CREATE TABLE t_call_log_2026_04
    PARTITION OF t_call_log
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
```

### 5.7 t_webhook_config

```sql
CREATE TABLE t_webhook_config (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    url             VARCHAR(500) NOT NULL,
    events          JSONB NOT NULL DEFAULT '[]',
    secret          TEXT NOT NULL,             -- AES 加密存储
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    retry_count     INT NOT NULL DEFAULT 5,
    create_user     BIGINT,
    create_time     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_user     BIGINT,
    update_time     TIMESTAMPTZ,
    deleted         SMALLINT NOT NULL DEFAULT 0
);
```

### 5.8 t_webhook_event

```sql
CREATE TABLE t_webhook_event (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    webhook_id      BIGINT NOT NULL,            -- 关联 t_webhook_config.id
    tenant_id       BIGINT NOT NULL,
    event_type      VARCHAR(50) NOT NULL,        -- quota.warning / quota.exceeded / appkey.disabled / tenant.suspended
    payload         JSONB NOT NULL,              -- 事件内容
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',  -- pending / success / failed / permanent_failed
    attempt_count   INT NOT NULL DEFAULT 0,      -- 已重试次数
    next_retry_time TIMESTAMPTZ,                 -- 下次重试时间
    last_error      TEXT,                        -- 最近一次失败原因
    http_status     SMALLINT,                    -- 最近一次回调响应状态码
    create_time     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ
);
CREATE INDEX idx_webhook_event_status ON t_webhook_event(status, next_retry_time)
    WHERE status IN ('pending', 'failed');
CREATE INDEX idx_webhook_event_tenant ON t_webhook_event(tenant_id);
```

> **重试策略**：`attempt_count` 达到 `t_webhook_config.retry_count` 上限后，状态置为 `permanent_failed`。后台定时任务按 `next_retry_time` 扫描待重试事件，指数退避（1min → 5min → 30min → 2h → 6h）。

### 5.9 t_rag_config

```sql
CREATE TABLE t_rag_config (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           BIGINT NOT NULL,
    name                VARCHAR(100) NOT NULL,
    description         TEXT,
    embedding_model     VARCHAR(100) NOT NULL DEFAULT 'text-embedding-v4',
    vector_dimension    INT NOT NULL DEFAULT 1536,
    top_k               INT NOT NULL DEFAULT 5,
    similarity_threshold FLOAT NOT NULL DEFAULT 0.7,
    status              VARCHAR(20) NOT NULL DEFAULT 'active',
    create_user         BIGINT,
    create_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_user         BIGINT,
    update_time         TIMESTAMPTZ,
    deleted             SMALLINT NOT NULL DEFAULT 0
);
```

### 5.10 t_rag_document

```sql
CREATE TABLE t_rag_document (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rag_config_id       BIGINT NOT NULL,
    title               VARCHAR(200) NOT NULL,
    content             TEXT,                  -- 或存储文件路径
    file_path           VARCHAR(500),
    embedding_status    VARCHAR(20) NOT NULL DEFAULT 'pending',
    chunk_size          INT NOT NULL DEFAULT 500,
    chunk_overlap       INT NOT NULL DEFAULT 50,
    create_user         BIGINT,
    create_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_user         BIGINT,
    update_time         TIMESTAMPTZ,
    deleted             SMALLINT NOT NULL DEFAULT 0
);
```

### 5.11 t_agent_config

```sql
CREATE TABLE t_agent_config (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    name            VARCHAR(100) NOT NULL,
    system_prompt   TEXT,
    model           VARCHAR(100) NOT NULL DEFAULT 'qwen3.6-max-preview',
    temperature     FLOAT NOT NULL DEFAULT 0.7,
    max_tokens      INT NOT NULL DEFAULT 2048,
    tools           JSONB NOT NULL DEFAULT '[]',
    tts_config      JSONB,                   -- {enabled, voice, speed, pitch, volume}
    asr_config      JSONB,                   -- {enabled, language, model, enable_punctuation}
    rag_config_id   BIGINT,                  -- 关联 t_rag_config
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    create_user     BIGINT,
    create_time     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_user     BIGINT,
    update_time     TIMESTAMPTZ,
    deleted         SMALLINT NOT NULL DEFAULT 0
);
```

### 5.12 t_workflow_config

```sql
CREATE TABLE t_workflow_config (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    version         INT NOT NULL DEFAULT 1,
    entry_node_id   BIGINT,
    status          VARCHAR(20) NOT NULL DEFAULT 'draft',
    create_user     BIGINT,
    create_time     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_user     BIGINT,
    update_time     TIMESTAMPTZ,
    deleted         SMALLINT NOT NULL DEFAULT 0
);
```

### 5.13 t_workflow_node

```sql
CREATE TABLE t_workflow_node (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workflow_id     BIGINT NOT NULL,
    node_type       VARCHAR(20) NOT NULL,    -- start / llm / rag / tool / condition / end / tts / asr
    name            VARCHAR(100) NOT NULL,
    config          JSONB NOT NULL DEFAULT '{}',
    next_nodes      JSONB NOT NULL DEFAULT '[]',
    position_x      FLOAT,
    position_y      FLOAT,
    create_user     BIGINT,
    create_time     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_user     BIGINT,
    update_time     TIMESTAMPTZ,
    deleted         SMALLINT NOT NULL DEFAULT 0
);
```

### 5.14 t_tenant_brain_config

```sql
CREATE TABLE t_tenant_brain_config (
    tenant_id           BIGINT PRIMARY KEY,     -- 关联 t_tenant.id
    rag_config          JSONB,
    agent_config        JSONB,
    workflow_config     JSONB,
    version             INT NOT NULL DEFAULT 1,
    dispatch_status     VARCHAR(20) NOT NULL DEFAULT 'pending',
    -- 取值：pending（待下发）/ dispatched（已下发并确认）/ failed（下发失败）
    dispatch_time       TIMESTAMPTZ,
    edge_ack_time       TIMESTAMPTZ,
    create_user         BIGINT,
    create_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_user         BIGINT,
    update_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted             SMALLINT NOT NULL DEFAULT 0
);
```

---

## 6. API 接口规范

### 6.1 通用响应格式

**成功**：
```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**失败**：
```json
{
  "code": 40102,
  "error_code": "APP_KEY_NOT_FOUND",
  "message": "AppKey 不存在",
  "data": null,
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 6.2 分页响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [...],
    "total": 100,
    "page": 1,
    "pageSize": 20
  }
}
```

### 6.3 Portal API 接口清单（/api/portal/v1）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | 租户登录 |
| POST | `/auth/register` | 租户注册 |
| POST | `/auth/logout` | 登出 |
| POST | `/auth/refresh` | 刷新 Token |
| GET | `/credentials` | AppKey 列表 |
| POST | `/credentials` | 创建 AppKey |
| PUT | `/credentials/{id}/disable` | 禁用 AppKey |
| PUT | `/credentials/{id}/enable` | 启用 AppKey |
| PUT | `/credentials/{id}/rotate` | 轮换 SecretKey |
| DELETE | `/credentials/{id}` | 删除 AppKey |
| GET | `/logs` | 调用日志列表 |
| GET | `/logs/stats` | 调用统计聚合 |
| GET | `/logs/export` | 导出 CSV |
| GET | `/webhooks` | Webhook 列表 |
| POST | `/webhooks` | 创建 Webhook |
| PUT | `/webhooks/{id}` | 更新 Webhook |
| DELETE | `/webhooks/{id}` | 删除 Webhook |
| POST | `/webhooks/{id}/test` | 测试推送 |
| GET | `/quota` | 查看配额使用量 |
| GET | `/tenant/profile` | 租户基本信息 |
| GET | `/rag` | RAG 知识库列表 |
| POST | `/rag` | 创建知识库 |
| GET | `/rag/{id}` | 知识库详情 |
| PUT | `/rag/{id}` | 更新知识库 |
| DELETE | `/rag/{id}` | 删除知识库 |
| POST | `/rag/{id}/documents` | 上传文档 |
| DELETE | `/rag/{id}/documents/{docId}` | 删除文档 |
| POST | `/rag/{id}/reindex` | 重新向量化 |
| POST | `/rag/{id}/search` | 检索测试，返回格式见下方 |

**`POST /rag/{id}/search` 返回格式**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      { "chunk": "文档片段内容...", "score": 0.85, "title": "文档标题" },
      { "chunk": "另一段内容...", "score": 0.72, "title": "文档标题" }
    ]
  }
}
```
| GET | `/agent` | Agent 配置列表 |
| POST | `/agent` | 创建 Agent |
| GET | `/agent/{id}` | Agent 详情 |
| PUT | `/agent/{id}` | 更新 Agent |
| DELETE | `/agent/{id}` | 删除 Agent |
| POST | `/agent/{id}/debug` | Agent 调试 |
| GET | `/workflow` | Workflow 列表 |
| POST | `/workflow` | 创建 Workflow |
| GET | `/workflow/{id}` | Workflow 详情 |
| PUT | `/workflow/{id}` | 更新 Workflow |
| DELETE | `/workflow/{id}` | 删除 Workflow |
| POST | `/workflow/{id}/publish` | 发布 Workflow（创建版本） |
| POST | `/workflow/{id}/run` | 运行测试 Workflow |
| GET | `/workflow/{id}/versions` | Workflow 版本历史 |
| POST | `/workflow/{id}/rollback` | 回滚到指定版本 |

### 6.4 Admin API 接口清单（/api/admin/v1）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | Admin 登录 |
| GET | `/tenants` | 租户列表 |
| GET | `/tenants/{id}` | 租户详情 |
| PUT | `/tenants/{id}/suspend` | 暂停租户 |
| PUT | `/tenants/{id}/restore` | 恢复租户 |
| PUT | `/tenants/{id}/plan` | 修改绑定套餐 |
| GET | `/plans` | 套餐列表 |
| POST | `/plans` | 创建套餐 |
| PUT | `/plans/{id}` | 更新套餐 |
| DELETE | `/plans/{id}` | 删除套餐（软删除） |
| GET | `/logs` | 全平台调用日志 |
| GET | `/overview/stats` | 系统概览统计 |
| GET | `/rag` | 全平台 RAG 知识库管理 |
| GET | `/agent` | 全平台 Agent 配置管理 |
| GET | `/workflow` | 全平台 Workflow 管理 |
| GET | `/brain-configs` | 租户大脑配置下发状态 |
| POST | `/brain-configs/{tenantId}/dispatch` | **异步**触发配置下发（立即返回 pending，不等待 ACK） |

---

## 7. 邮件服务规划（Phase 3 引入）

| 场景 | 邮件内容 | 优先级 |
|------|---------|--------|
| 邮箱验证码 | 租户注册验证 | P1（Phase 3）|
| 配额告警 | 月配额使用超 80% | P2（Phase 3）|
| 配额耗尽 | 月配额已用完 | P2（Phase 3）|
| SecretKey 轮换提醒 | 旧 SecretKey 即将失效 | P3（Phase 5）|

**技术方案**：Spring Boot Starter Mail + SMTP（或阿里云邮件推送）

---

## 8. 里程碑规划

### Phase 1 — 基础开放平台（MVP）

**目标**：租户可以注册、管理 AppKey、调用 Open API

| 模块 | 工作内容 |
|------|---------|
| Backend | 租户域、AppKey 域、基础鉴权接口 |
| Edge | 网关搭建、鉴权过滤器、调用日志 |
| Frontend | Portal 登录/注册、AppKey CRUD |

### Phase 2 — 配额与监控

| 模块 | 工作内容 |
|------|---------|
| Backend | 配额域、调用日志统计 API、邮件服务 |
| Edge | 限流过滤器（QPS + 月配额） |
| Frontend | Portal 调用日志查询、统计图表 |

### Phase 3 — Webhook + Admin 后台

| 模块 | 工作内容 |
|------|---------|
| Backend | Webhook 域、事件发布、推送重试 |
| Frontend | Portal Webhook 管理、Admin Console |

### Phase 4 — AI 大脑（中枢配置层 + Edge 执行层）

**目标**：租户可配置 AI 大脑，Edge 大脑组装执行

| 模块 | 工作内容 |
|------|---------|
| Backend（中枢） | RAG 配置域、Agent 配置域、Workflow 配置域、配置下发域（WebSocket） |
| Edge（大脑） | 大脑组装服务、RAG 检索器、Agent 执行器、Workflow 引擎、TTS/ASR 客户端、Tool 注册表 |
| Frontend（中枢） | RAG 知识库管理、Agent 配置（含 TTS/ASR）、Workflow 可视化编排、配置下发状态 |

### Phase 5 — 完善与优化

- 调用日志 CSV 导出
- 套餐管理可视化
- 系统大盘看板
- Workflow 高级节点（循环、并行、子流程）
- Agent 多模态支持（图片、语音输入）
- 性能压测与调优

---

## 9. Open API 业务接口（Edge 大脑 — 执行层）

平台对外暴露的业务接口（供第三方通过 AppKey 调用），由 **Edge 大脑**执行：

### 9.1 AI 对话接口

```http
POST /api/open/v1/ai/chat
Content-Type: application/json
X-App-Key: {appKey}
X-Timestamp: {timestamp}
X-Nonce: {nonce}
X-Sign: {sign}

{
  "message": "你好，请介绍一下自己",
  "session_id": "optional-session-id",
  "stream": false
}
```

**非流式响应示例**（`stream: false`）：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "reply": "你好！我是 Lazyday 平台的 AI 助手...",
    "session_id": "sess_12345",
    "usage": {
      "prompt_tokens": 120,
      "completion_tokens": 280,
      "total_tokens": 400
    }
  },
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**流式响应示例**（`stream: true`，SSE 格式）：
```
data: {"type":"start","session_id":"sess_12345"}
data: {"type":"delta","content":"你好"}
data: {"type":"delta","content":"！"}
data: {"type":"done","usage":{"total_tokens":400}}
```

**处理流程**：
1. Edge 大脑鉴权 + 限流
2. 加载租户 Agent 配置 + 关联 RAG 知识库
3. 如有 RAG，先执行检索获取上下文
4. 组装 Prompt（系统提示词 + RAG 上下文 + 用户消息）
5. 调用 LLM（DashScope Qwen）生成回复
6. 如启用 TTS，同步返回语音 URL
7. 推送调用日志至中枢 Backend

### 9.2 语音合成接口（TTS）

```http
POST /api/open/v1/ai/tts
Content-Type: application/json

{
  "text": "你好，这是语音合成测试",
  "voice": "xiaoyun",      // 可选，覆盖默认 Agent TTS 配置
  "speed": 1.0             // 可选，覆盖默认配置
}
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "audio_url": "https://cdn.lazyday.com/tts/xxx.mp3",
    "duration_ms": 1500
  }
}
```

### 9.3 语音识别接口（ASR）

```http
POST /api/open/v1/ai/asr
Content-Type: multipart/form-data

file: [音频文件]
language: zh-CN           // 可选，覆盖默认 Agent ASR 配置
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "text": "你好，这是语音识别结果",
    "confidence": 0.95
  }
}
```

### 9.4 Workflow 执行接口

```http
POST /api/open/v1/ai/workflow
Content-Type: application/json

{
  "workflow_id": "550e8400-e29b-41d4-a716-446655440000",
  "parameters": {
    "query": "查询订单状态",
    "order_id": "ORD-12345"
  }
}
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "execution_id": "exec_abc123",
    "result": {
      "node_id": "end_node",
      "output": "您的订单已发货，预计明天送达"
    },
    "nodes_executed": ["start", "llm_node", "end"]
  }
}
```

**处理流程**：
1. Edge 大脑加载租户 Workflow 配置（从 L1/L2 缓存）
2. 从入口节点开始执行
3. 按节点类型处理：
   - LLM 节点：调用 Agent 生成回复
   - RAG 节点：检索知识库
   - Tool 节点：调用 Edge 本地 Tool 执行器
   - TTS 节点：语音合成
   - ASR 节点：语音识别
   - 条件节点：根据 SpEL 表达式（如 `{{input.age}} > 18`）分支
4. 收集各节点输出，组装最终响应

### 9.5 完整 Agent 交互接口

```http
POST /api/open/v1/ai/agent
Content-Type: application/json

{
  "agent_id": "550e8400-e29b-41d4-a716-446655440000",
  "message": "你好",
  "input_type": "text",     // text / voice
  "output_type": "text"     // text / voice / both
}
```

**支持模式**：
- 文本输入 → 文本输出（默认）
- 文本输入 → 语音输出（TTS 启用）
- 语音输入 → 文本输出（ASR 启用）
- 语音输入 → 语音输出（ASR + TTS 启用）

> 所有 Open API 均经过 Edge 大脑的 **AppKey 鉴权 + 限流/配额** 校验，响应格式遵循第 6 节通用响应规范。

---

## 10. 会话存储设计

AI 对话接口支持多轮对话（`session_id`），需要在请求间保持对话上下文。

### 10.1 存储方案

| 层级 | 存储 | TTL | 说明 |
|------|------|-----|------|
| L1 | Edge 本地 Caffeine | 5 分钟 | 热会话零网络开销 |
| L2 | Redis（`session:{sessionId}`） | 可配置，默认 2 小时 | 跨 Edge 实例共享 |

### 10.2 数据结构

```json
{
  "session_id": "sess_12345",
  "tenant_id": 1001,
  "agent_id": 2001,
  "messages": [
    {"role": "user", "content": "你好"},
    {"role": "assistant", "content": "你好！我是 AI 助手..."},
    {"role": "user", "content": "帮我查一下订单"}
  ],
  "created_at": 1714061234567,
  "updated_at": 1714061334567
}
```

### 10.3 设计要点

- **Edge 不持久化**：会话数据存入 Redis，Edge 本地仅做 Caffeine 热缓存。Edge 重启后从 Redis 恢复。
- **上下文窗口**：`messages` 数组保留最近 N 轮对话（默认 20 轮），超出后滑动窗口截断旧消息。
- **会话隔离**：`session_id` 由 Edge 生成（UUID），绑定 `tenant_id` + `agent_id`，防止跨租户访问。
- **过期清理**：Redis TTL 到期自动清理。租户可通过 Agent 配置自定义会话超时时长。

---

## 11. 安全设计补充

### 11.1 SpEL 表达式安全

Workflow 条件节点支持 SpEL 表达式（如 `{{input.age}} > 18`）。SpEL 是图灵完备的，存在远程代码执行（RCE）风险。

**安全措施**：
- 使用 `SimpleEvaluationContext`（Spring 内置安全沙箱）替代默认的 `StandardEvaluationContext`
- **白名单校验**：仅允许比较运算（`>`, `<`, `==`, `!=`）、逻辑运算（`&&`, `||`, `!`）、属性访问（`{{input.xxx}}`）
- **黑名单拦截**：禁止 `T()` 类型引用、`Runtime` 调用、`ProcessBuilder`、`new` 关键字等危险操作
- 表达式长度限制（≤ 500 字符）
- Backend 保存配置时做静态校验，不合规则拒绝保存