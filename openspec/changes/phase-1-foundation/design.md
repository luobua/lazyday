## Context

Lazyday Backend 已有完整的 DDD 分层骨架（interfaces / application / domain / infrastructure），基础设施包括 R2DBC 连接池、Flyway 迁移、AES/RSA 加密工具、Snowflake ID 生成器、ContextPath 路由注解（已有 V1/V2/OpenV1）。Frontend 已有 Next.js 15 Monorepo（Portal + Admin 双应用），包含 Ant Design 5.x 组件、api-client 全套接口定义（mock 状态）、Zustand 状态管理、TanStack Query Provider、Middleware 路由守卫。

Phase 1 需要在现有骨架上构建租户体系、凭证管理和三层鉴权。Edge 网关延后到 Phase 2。

## Goals / Non-Goals

**Goals:**
- 开发者可注册租户、登录、管理 AppKey
- 管理员可通过独立入口登录后台
- 三层安全防护：纵向（角色→路径）+ 横向（租户数据隔离）+ CSRF
- 前端 Portal 页面对接真实 Backend API
- 统一 API 响应格式
- Dark Mode OLED 设计系统落地

**Non-Goals:**
- Open API（HMAC 验签）— 延后到 Phase 2 与 Edge 一起交付
- Edge 网关搭建 — Phase 2
- 配额管理 / 调用日志 — Phase 2
- Admin Console 租户管理页面 — Phase 3
- AI 能力 — Phase 4

## Decisions

### D1: JWT 签发方式 — RSA 非对称签名

**选择**: RS256（RSA + SHA-256）

**替代方案**: HS256（HMAC 对称密钥）

**理由**: 项目已有 RSA 工具类（`infrastructure/utils/encrypt/RSA.java`），可直接复用。非对称签名允许 Phase 2 的 Edge 网关仅持有公钥验证，无需共享私钥，提升安全性。

### D2: Token 传递方式 — HttpOnly Cookie

**选择**: `Set-Cookie: access_token=xxx; HttpOnly; Secure; SameSite=Strict; Path=/`

**替代方案**: Authorization Bearer header + localStorage

**理由**: HttpOnly Cookie 天然防 XSS 窃取，SameSite=Strict 天然防 CSRF（第一层）。前端 api-client 已配置 `withCredentials: true`，代码改动最小。

### D3: 横向鉴权实现 — Repository 层强制 tenantId

**选择**: 所有 Portal 域的 Repository 方法签名强制携带 tenantId 参数，从 Reactor Context 中的 TenantContext 获取。

**替代方案 1**: AOP 切面自动注入 — 隐式注入容易遗漏，debug 困难

**替代方案 2**: 数据库 RLS（Row Level Security）— R2DBC 连接池共享连接，设置 session 变量有并发风险

**理由**: 显式参数最安全、最可控。Repository 方法不带 tenantId 则编译不过，杜绝遗漏。性能零开销。

### D4: CSRF 方案 — 双重提交 Cookie 模式

**选择**: 
1. SameSite=Strict Cookie（被动防护，D2 已覆盖）
2. 双重提交 Cookie：`GET /auth/csrf-token` 返回 token 写入 Cookie，前端请求时通过 `X-CSRF-Token` header 回传，服务端比对

**替代方案**: Synchronizer Token Pattern（服务端存储 token）— 需要 session 存储，与无状态 JWT 方案冲突

**理由**: 双重提交 Cookie 无状态、与 JWT 方案一致。SameSite=Strict 已提供第一层防护，CSRF Token 作为纵深防御。

### D5: 前端色板 — Ant Design ConfigProvider Token 覆盖

**选择**: 通过 `ConfigProvider` 的 `theme.token` 覆盖 Ant Design 默认色板，实现 Dark Mode OLED

**替代方案**: CSS 变量全局覆盖 — 与 Ant Design 内部样式冲突，维护成本高

**理由**: Ant Design 5.x 原生支持 Token 系统，一处配置全局生效。色板定义在 `design-system/lazyday-open-platform/MASTER.md`。

### D6: 密码哈希 — bcrypt

**选择**: bcrypt（cost factor 10）

**替代方案**: Argon2id — 更强但需额外依赖

**理由**: Spring Security Crypto 内置 BCryptPasswordEncoder，零依赖引入。cost=10 在现代硬件上约 100ms/次，足够安全。

### D7: SecretKey 加密存储 — AES-256

**选择**: 使用已有 `AES.java` 工具类加密 SecretKey，密文存储到 `secret_key_encrypted` 字段

**理由**: 验签时需要还原明文计算 HMAC，因此不能用单向哈希。AES 可逆加密满足需求。加密主密钥通过 `ServiceProperties.secretKey` 管理。

### D8: AppKey 轮换机制 — 宽限期模式

**选择**: 轮换时生成新密钥 → 旧密钥移入 `secret_key_old` → `grace_period_end` 设为当前时间 +24h → 宽限期内新旧均可验签 → 过期后 old 置空

**替代方案**: 立即失效 — 对调用方不友好

**理由**: 24h 宽限期给调用方足够的迁移窗口，同时限制旧密钥暴露时间。

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| JWT 泄露无法主动吊销 | Access Token 2h 短过期 + Phase 2 可引入 Redis 黑名单 |
| bcrypt 阻塞 Reactor 线程 | 使用 `Mono.fromCallable().subscribeOn(Schedulers.boundedElastic())` 将计算移到弹性线程池 |
| CSRF Token 首次获取需额外请求 | 前端拦截器在首次 POST 前自动获取并缓存，后续请求直接使用 |
| 横向鉴权依赖开发者纪律（Repository 参数） | Code Review 阶段重点检查；后续可加 ArchUnit 规则自动检测 |
| Dark Mode 下 Ant Design 部分组件对比度不足 | 交付前用 ui-ux-pro-max Pre-Delivery Checklist 逐项验证 |
| Flyway 迁移脚本与现有空目录冲突 | 确认 `db/migration/` 目录为空（已验证），V1 脚本为首个迁移 |