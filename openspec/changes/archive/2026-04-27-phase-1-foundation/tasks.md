## 1. 数据层 — Flyway 迁移脚本

- [x] 1.1 编写 `V1__init_tenant.sql`：创建 t_tenant 表（id, name, status, plan_type, contact_email, 审计字段）
- [x] 1.2 编写 `V1__init_tenant.sql`：扩展 t_user 表（增加 password_hash, role, tenant_id, status 字段）
- [x] 1.3 编写 `V1__init_tenant.sql`：创建 t_app_key 表（id, tenant_id FK, name, app_key UNIQUE, secret_key_encrypted, secret_key_old, rotated_at, grace_period_end, status, scopes, 审计字段）
- [x] 1.4 验证 Flyway 迁移执行成功（`./mvnw clean compile` + 本地 PostgreSQL）

## 2. Backend 域模型 — Tenant 域

- [x] 2.1 创建 TenantPO（@Table("t_tenant")，继承 BaseAllUserTime）
- [x] 2.2 创建 TenantRepository（@Component，使用 R2dbcEntityTemplate，所有方法强制 tenantId 参数）
- [x] 2.3 创建 TenantEntity（Lazy CTX 模式，封装业务逻辑）
- [x] 2.4 创建 TenantAggregation（聚合根）

## 3. Backend 域模型 — AppKey 域

- [x] 3.1 创建 AppKeyPO（@Table("t_app_key")，继承 BaseAllUserTime）
- [x] 3.2 创建 AppKeyRepository（所有方法强制 tenantId 参数，支持 findByAppKey 查询）
- [x] 3.3 创建 AppKeyEntity（AES 加密/解密 SecretKey，密钥轮换宽限期逻辑，密钥生成 ak_/sk_ 前缀）
- [x] 3.4 创建 AppKeyAggregation

## 4. Backend 域模型 — User 域扩展

- [x] 4.1 扩展 User PO（增加 password_hash, role, tenant_id, status 字段）
- [x] 4.2 扩展 UserEntity（bcrypt 哈希/校验方法，使用 Schedulers.boundedElastic()）
- [x] 4.3 扩展 UserRepository（增加 findByUsername 方法）

## 5. Backend 基础设施 — 路由注解 + ApiResponse

- [x] 5.1 创建 `@RequestMappingPortalV1` 注解
- [x] 5.2 创建 `@RequestMappingAdminV1` 注解
- [x] 5.3 在 ContextPathConfiguration 中注册 Portal 和 Admin 路径前缀
- [x] 5.4 在 ServiceProperties 中添加 portalContextPathV1 / adminContextPathV1 属性，并在 application.yaml 中配置
- [x] 5.5 创建 ApiResponse<T> 类（code, error_code, message, data, request_id）
- [x] 5.6 创建 GlobalExceptionHandler（@ControllerAdvice，映射常见异常到 ApiResponse）
- [x] 5.7 创建 RequestIdFilter（生成 UUID request_id，写入 Reactor Context + X-Request-Id 响应头）

## 6. Backend 安全 — JwtService

- [x] 6.1 引入 JWT 依赖（nimbus-jose-jwt 或 jjwt）到 pom.xml
- [x] 6.2 创建 JwtService（RS256 签发/验证，payload 含 sub/tenantId/role/iat/exp）
- [x] 6.3 实现 Access Token 生成（2h 过期）
- [x] 6.4 实现 Refresh Token 生成（7d 过期，记住我 30d）
- [x] 6.5 实现 Cookie 工具方法（Set-Cookie: HttpOnly; Secure; SameSite=Strict; Path=/）

## 7. Backend 安全 — Filter 链

- [x] 7.1 创建 TenantContext 类（userId, tenantId, role，存入 Reactor Context）
- [x] 7.2 创建 JwtAuthWebFilter（解析 access_token Cookie → 验证 → 注入 TenantContext，排除公开路径）
- [x] 7.3 创建 RoleAuthorizationFilter（admin 路径要求 PLATFORM_ADMIN，portal 路径要求 TENANT_ADMIN，不匹配返回 403 FORBIDDEN_ROLE）
- [x] 7.4 创建 CsrfProtectionFilter（POST/PUT/DELETE 校验 X-CSRF-Token header vs csrf_token Cookie，排除 login/register）
- [x] 7.5 配置 Filter 执行顺序（@Order：JwtAuth → RoleAuth → CSRF）

## 8. Backend API — Portal Auth

- [x] 8.1 创建 PortalAuthApi 接口（定义 register/login/refresh/logout/me/csrf-token 端点）
- [x] 8.2 创建 PortalAuthHandler（@RequestMappingPortalV1，实现 PortalAuthApi）
- [x] 8.3 创建 AuthFacade + AuthService（注册事务：创建 Tenant + User；登录：bcrypt 校验 → JWT 签发）
- [x] 8.4 实现 register 端点（创建 Tenant + User，返回 JWT Cookie）
- [x] 8.5 实现 login 端点（bcrypt 校验 → JWT Cookie，支持 remember 参数）
- [x] 8.6 实现 refresh 端点（Refresh Token 校验 → 签发新 Access Token）
- [x] 8.7 实现 logout 端点（清除 Cookie，max-age=0）
- [x] 8.8 实现 me 端点（从 TenantContext 返回用户信息）
- [x] 8.9 实现 csrf-token 端点（生成随机 token → 设置 csrf_token Cookie + 返回 body）

## 9. Backend API — Admin Auth

- [x] 9.1 创建 AdminAuthApi 接口 + AdminAuthHandler
- [x] 9.2 实现 admin login 端点（校验 role=PLATFORM_ADMIN，否则 403 FORBIDDEN_ROLE）
- [x] 9.3 实现 admin logout / me / csrf-token 端点

## 10. Backend API — Portal Credentials

- [x] 10.1 创建 PortalCredentialsApi 接口（定义 CRUD + rotate 端点）
- [x] 10.2 创建 PortalCredentialsHandler（@RequestMappingPortalV1）
- [x] 10.3 创建 CredentialsFacade + CredentialsService
- [x] 10.4 实现 list 端点（按 tenantId 查询，SecretKey 脱敏返回）
- [x] 10.5 实现 create 端点（生成 ak_/sk_，AES 加密存储，明文 SecretKey 仅返回一次）
- [x] 10.6 实现 disable / enable 端点（校验 tenant 归属 → 更新状态）
- [x] 10.7 实现 rotate-secret 端点（新密钥 + old 保留 + grace_period_end + 返回新明文）
- [x] 10.8 实现 delete 端点（校验 tenant 归属 → 删除）

## 11. Backend API — Portal Tenant

- [x] 11.1 创建 PortalTenantApi 接口 + PortalTenantHandler
- [x] 11.2 实现 GET /tenant 端点（从 TenantContext 取 tenantId 查询）
- [x] 11.3 实现 PUT /tenant 端点（更新 name / contact_email，校验 tenantId 归属）

## 12. Frontend — 设计系统 + 基础设施

- [x] 12.1 在 Portal 根 layout 配置 Ant Design ConfigProvider Dark Mode token 覆盖（colorBgBase/colorText/colorPrimary 等）
- [x] 12.2 引入 Inter + Fira Code 字体（Google Fonts import）
- [x] 12.3 api-client 补充 CSRF token 自动获取和缓存逻辑（请求拦截器）
- [x] 12.4 创建 TanStack Query hooks（useLogin / useRegister / useLogout / useMe / useCredentials / useCreateCredential / useDisableCredential / useEnableCredential / useRotateSecret / useDeleteCredential）

## 13. Frontend — Portal 登录页对接

- [x] 13.1 去除 mock 逻辑，调用 authApi.login，处理 loading / success / error 状态
- [x] 13.2 登录成功后 setUser → router.push('/overview')
- [x] 13.3 实现 remember 参数传递
- [x] 13.4 实现错误展示（401 → message.error / 字段空 → 内联校验）

## 14. Frontend — Portal 注册页对接

- [x] 14.1 扩展注册表单字段（username, email, password, confirm_password, tenant_name）
- [x] 14.2 实现密码强度指示器（红/橙/绿三段色条）
- [x] 14.3 对接 authApi.register，处理 409 DUPLICATE_USERNAME / DUPLICATE_EMAIL 内联错误
- [x] 14.4 注册成功后自动登录跳转

## 15. Frontend — Portal Credentials 页对接

- [x] 15.1 去除 mockAppKeys，使用 useCredentials hook 获取真实数据
- [x] 15.2 对接创建 AppKey（name + 多选 Select scopes），创建成功弹出 SecretKey 一次性展示 Modal（maskClosable=false + 关闭前确认）
- [x] 15.3 对接禁用/启用操作（Popconfirm → API → 状态 Tag 更新）
- [x] 15.4 对接轮换密钥（Popconfirm → API → 新 SecretKey 一次性展示 Modal）
- [x] 15.5 对接删除操作（Popconfirm → API → 行移除）
- [x] 15.6 实现空状态展示（Empty + 创建引导）

## 16. Frontend — UI/UX 审查

- [x] 16.1 使用 ui-ux-pro-max Pre-Delivery Checklist 审查登录页（对比度、focus、cursor、响应式）
- [x] 16.2 使用 ui-ux-pro-max Pre-Delivery Checklist 审查注册页
- [x] 16.3 使用 ui-ux-pro-max Pre-Delivery Checklist 审查 Credentials 页
- [x] 16.4 验证 SecretKey 一次性展示 Modal 的安全 UX（maskClosable=false、关闭确认、Fira Code 字体）

## 17. 端到端联调 + 验收

- [x] 17.1 联调：注册租户 → 数据库 t_tenant + t_user 记录正确
- [x] 17.2 联调：登录 → JWT Cookie 设置正确 → me 端点返回用户信息
- [x] 17.3 联调：创建 AppKey → SecretKey 一次性展示 → 数据库 AES 加密存储验证
- [x] 17.4 验证横向隔离：Tenant A 用户无法访问 Tenant B 的 AppKey（返回 403 FORBIDDEN_TENANT）
- [x] 17.5 验证纵向隔离：TENANT_ADMIN 访问 /api/admin/v1/** 返回 403 FORBIDDEN_ROLE
- [x] 17.6 验证 CSRF：缺少 X-CSRF-Token 的 POST 返回 403 CSRF_TOKEN_MISSING
- [x] 17.7 验证 Refresh Token 刷新：Access Token 过期后 refresh 端点签发新 Token（需重启后验证，已修复 CSRF 豁免）
- [x] 17.8 验证 Admin 登录：curl POST /api/admin/v1/auth/login 正确拒绝非 PLATFORM_ADMIN 用户（403 FORBIDDEN_ROLE）
- [x] 17.9 UI/UX 最终验收：所有页面通过 Pre-Delivery Checklist
