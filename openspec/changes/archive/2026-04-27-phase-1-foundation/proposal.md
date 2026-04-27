## Why

Lazyday 开放平台目前只有 DDD 分层骨架和示例代码（Demo + User 域），缺少租户体系、凭证管理和鉴权能力。开发者无法注册租户、创建 API 凭证、也无法通过鉴权访问任何受保护接口。Phase 1 需要建立完整的身份认证和授权基础设施，为后续所有阶段（配额、Webhook、AI 大脑）提供安全底座。

## What Changes

**Backend**
- 新增 Flyway 迁移脚本 `V1__init_tenant.sql`（t_user 扩展 + t_tenant + t_app_key）
- 新增 Tenant 域（TenantAggregation / TenantEntity / TenantRepository）
- 新增 AppKey 域（AppKeyAggregation / AppKeyEntity / AppKeyRepository，含 AES 加密存储 + 密钥轮换宽限期）
- 扩展 User 域（bcrypt 密码哈希 + role 字段 + tenant_id 关联）
- 新增 `@RequestMappingPortalV1` + `@RequestMappingAdminV1` 路由注解及 ContextPath 配置
- 新增通用 `ApiResponse<T>` 响应格式 + `GlobalExceptionHandler`
- 新增 JwtService（RSA 签发，Access Token 2h / Refresh Token 7d / 记住我 30d）
- 新增安全 Filter 链：JwtAuthWebFilter → RoleAuthorizationFilter（纵向鉴权）→ TenantIsolationAspect（横向鉴权）→ CsrfProtectionFilter
- 新增 Portal Auth API（register / login / refresh / logout / me / csrf-token）
- 新增 Admin Auth API（login / logout / me / csrf-token）
- 新增 Portal Credentials API（AppKey CRUD + SecretKey 轮换）
- 新增 Portal Tenant API（租户信息查看 + 更新）

**Frontend**
- Portal 登录 / 注册页面对接真实 API（去 mock）
- Portal Credentials 页面对接真实 CRUD API（去 mock）
- api-client 补充 CSRF Token 自动携带
- 新增 TanStack Query hooks 层
- Ant Design 5.x Token 覆盖：Dark Mode OLED 色板 + Inter 字体

## Capabilities

### New Capabilities

- `tenant-management`: 租户注册、信息管理、租户数据模型
- `user-auth`: 用户认证体系（bcrypt + JWT + Refresh Token + 记住我 + Cookie 管理）
- `appkey-management`: API 凭证全生命周期管理（创建 / 禁用 / 启用 / 删除 / SecretKey AES 加密 + 轮换宽限期）
- `authorization`: 三层鉴权体系（纵向角色鉴权 + 横向租户隔离 + CSRF 防护）
- `api-response`: 统一 API 响应格式 + 全局异常处理
- `portal-ui`: Portal 前端页面（登录 / 注册 / AppKey 管理）— Dark Mode 设计系统

### Modified Capabilities

（无现有 spec，均为新建）

## Impact

- **数据库**: 新增 3 张核心表（t_user 扩展 / t_tenant / t_app_key），需 PostgreSQL 实例就绪
- **Backend API**: 新增 `/api/portal/v1/**` 和 `/api/admin/v1/**` 两组路由前缀，现有 `/api/lazyday/v1/**` 不受影响
- **Frontend**: Portal 和 Admin 应用从 mock 切换到真实 API，需 Backend 先行就绪
- **依赖**: 新增 spring-security-crypto（bcrypt）、jjwt 或 nimbus-jose-jwt（JWT）
- **配置**: `ServiceProperties` 新增 portalContextPathV1 / adminContextPathV1 属性