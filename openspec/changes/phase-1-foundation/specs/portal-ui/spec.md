## ADDED Requirements

### Requirement: Dark Mode design system
The Portal application SHALL apply a Dark Mode OLED theme via Ant Design 5.x ConfigProvider token overrides. The color palette SHALL use: colorBgBase `#0F172A`, colorBgContainer `#1E293B`, colorText `#F8FAFC`, colorTextSecondary `#94A3B8`, colorPrimary `#1E293B`, colorSuccess `#22C55E`. Font SHALL be Inter for UI text and Fira Code for code/keys display.

#### Scenario: Theme applied globally
- **WHEN** the Portal application renders
- **THEN** all Ant Design components use the Dark Mode token overrides and Inter font family

### Requirement: Portal login page
The Portal login page SHALL display a centered card (max-width 420px) on a dark background (`#0F172A`). The form SHALL include username, password (with visibility toggle), "remember me" checkbox, and a login button. Links to registration and forgot-password pages SHALL be provided.

#### Scenario: Successful login
- **WHEN** a user submits valid credentials
- **THEN** the page shows a loading state on the button, calls `POST /api/portal/v1/auth/login`, updates Zustand auth store with user info, and navigates to `/overview`

#### Scenario: Login failure
- **WHEN** the API returns 401
- **THEN** the page displays `message.error('用户名或密码错误')` and re-enables the login button

#### Scenario: Empty fields
- **WHEN** the user submits without filling required fields
- **THEN** inline validation errors appear below each empty field

### Requirement: Portal registration page
The Portal registration page SHALL display a centered card matching login page layout. The form SHALL include username, email, password (with strength indicator), confirm password, and tenant name (5 fields maximum).

#### Scenario: Successful registration
- **WHEN** a user submits valid registration data
- **THEN** the page calls `POST /api/portal/v1/auth/register`, auto-logs in (JWT cookies set by backend), and navigates to `/overview`

#### Scenario: Duplicate username
- **WHEN** the API returns 409 with error_code `DUPLICATE_USERNAME`
- **THEN** the page shows inline error "用户名已被使用" under the username field and focuses that field

#### Scenario: Password mismatch
- **WHEN** the confirm password does not match the password
- **THEN** inline error "两次密码不一致" appears under the confirm password field on blur

#### Scenario: Password strength indicator
- **WHEN** the user types in the password field
- **THEN** a strength bar below the field updates: red (weak), orange (medium), green (strong) based on length + character diversity

### Requirement: Portal credentials page
The Portal credentials page SHALL display an AppKey management table with columns: name (+ status tag), AppKey (masked + copy button), scopes (tags), created time, and actions (detail / disable-enable / rotate / delete).

#### Scenario: List AppKeys
- **WHEN** the page loads
- **THEN** it fetches `GET /api/portal/v1/credentials` and displays all AppKeys in a table with loading skeleton during fetch

#### Scenario: Create AppKey
- **WHEN** a user clicks "创建 AppKey" and submits the form (name + multi-select scopes)
- **THEN** the system creates the AppKey and displays a one-time SecretKey modal (maskClosable=false) with the plaintext AppKey and SecretKey in copyable monospace text and a warning alert

#### Scenario: SecretKey modal close confirmation
- **WHEN** a user attempts to close the one-time SecretKey modal
- **THEN** the system shows a confirm dialog "确定已保存 SecretKey？关闭后无法再次查看" before allowing close

#### Scenario: Disable AppKey
- **WHEN** a user clicks "禁用" on an active AppKey
- **THEN** a Popconfirm asks "确定禁用此 AppKey？禁用后使用该 Key 的请求将被拒绝", on confirm calls the API, and updates the status tag to red "已禁用"

#### Scenario: Rotate SecretKey
- **WHEN** a user clicks "轮换密钥" and confirms the Popconfirm
- **THEN** the system calls the rotate API and displays a one-time modal with the new SecretKey and a note "旧密钥将在 24 小时后失效"

#### Scenario: Delete AppKey
- **WHEN** a user clicks "删除" and confirms
- **THEN** the system calls the delete API and removes the row from the table

#### Scenario: Empty state
- **WHEN** the tenant has no AppKeys
- **THEN** the table shows an Ant Design Empty component with text "还没有 AppKey" and a link to create one

### Requirement: CSRF token handling in api-client
The api-client request interceptor SHALL automatically obtain a CSRF token on the first state-changing request (POST/PUT/DELETE) by calling `GET /auth/csrf-token`, cache it, and include it as `X-CSRF-Token` header on all subsequent state-changing requests.

#### Scenario: First POST request
- **WHEN** the first POST request is made in a session
- **THEN** the interceptor first fetches a CSRF token, caches it, and includes it in the request header

#### Scenario: Subsequent POST requests
- **WHEN** subsequent POST/PUT/DELETE requests are made
- **THEN** the interceptor uses the cached CSRF token without re-fetching

### Requirement: TanStack Query hooks
The Portal SHALL use TanStack Query hooks for all API calls. Hooks SHALL handle loading, error, and success states. Mutation hooks SHALL invalidate relevant queries on success.

#### Scenario: useCredentials hook
- **WHEN** the credentials page mounts
- **THEN** `useCredentials()` fetches the AppKey list with loading/error states and caches the result

#### Scenario: Mutation invalidation
- **WHEN** a create/disable/enable/rotate/delete mutation succeeds
- **THEN** the credentials list query is automatically invalidated and refetched

### Requirement: Middleware route guard
The Next.js middleware SHALL redirect unauthenticated users (no access_token cookie) to `/login` for portal paths and to `/admin/login` for admin paths. Public paths (login, register, forgot-password) SHALL be excluded.

#### Scenario: Unauthenticated portal access
- **WHEN** a user without access_token cookie visits `/overview`
- **THEN** the middleware redirects to `/login?redirect=/overview`

#### Scenario: Authenticated access
- **WHEN** a user with a valid access_token cookie visits `/overview`
- **THEN** the middleware allows the request to proceed