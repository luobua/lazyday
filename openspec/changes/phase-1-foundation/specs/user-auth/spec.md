## ADDED Requirements

### Requirement: User login with JWT
The system SHALL authenticate users via username + bcrypt password verification and issue JWT tokens as HttpOnly cookies. Access Token SHALL expire in 2 hours. Refresh Token SHALL expire in 7 days. When "remember me" is checked, Refresh Token SHALL expire in 30 days.

#### Scenario: Successful Portal login
- **WHEN** a TENANT_ADMIN submits valid credentials to `POST /api/portal/v1/auth/login`
- **THEN** the system sets `access_token` and `refresh_token` as HttpOnly, Secure, SameSite=Strict cookies and returns user info (id, username, email, role, tenant_id)

#### Scenario: Successful Portal login with remember me
- **WHEN** a TENANT_ADMIN submits valid credentials with `remember: true`
- **THEN** the refresh_token cookie max-age SHALL be 30 days instead of 7 days

#### Scenario: Invalid credentials
- **WHEN** a user submits wrong username or password
- **THEN** the system returns HTTP 401 with error_code `INVALID_CREDENTIALS` and message "用户名或密码错误"

#### Scenario: Disabled account
- **WHEN** a user with status=DISABLED attempts to login
- **THEN** the system returns HTTP 403 with error_code `ACCOUNT_DISABLED`

### Requirement: Admin login
The system SHALL authenticate admin users via `POST /api/admin/v1/auth/login`. Only users with role `PLATFORM_ADMIN` SHALL be allowed.

#### Scenario: Successful Admin login
- **WHEN** a PLATFORM_ADMIN submits valid credentials to the admin login endpoint
- **THEN** the system sets JWT cookies and returns admin user info

#### Scenario: Non-admin attempts admin login
- **WHEN** a TENANT_ADMIN submits credentials to the admin login endpoint
- **THEN** the system returns HTTP 403 with error_code `FORBIDDEN_ROLE`

### Requirement: Token refresh
The system SHALL allow refreshing an expired access token using a valid refresh token.

#### Scenario: Successful refresh
- **WHEN** a request with expired access_token but valid refresh_token hits `POST /api/portal/v1/auth/refresh`
- **THEN** the system issues a new access_token cookie and returns HTTP 200

#### Scenario: Expired refresh token
- **WHEN** a request with expired refresh_token hits the refresh endpoint
- **THEN** the system returns HTTP 401 with error_code `REFRESH_TOKEN_EXPIRED`

### Requirement: Logout
The system SHALL clear all authentication cookies on logout.

#### Scenario: Successful logout
- **WHEN** a user requests `POST /api/portal/v1/auth/logout`
- **THEN** the system clears access_token and refresh_token cookies (max-age=0) and returns HTTP 200

### Requirement: Current user info
The system SHALL return the current authenticated user's info.

#### Scenario: Get current user
- **WHEN** an authenticated user requests `GET /api/portal/v1/auth/me`
- **THEN** the system returns user info (id, username, email, role, tenant_id) from the JWT context

### Requirement: JWT payload
JWT tokens SHALL contain userId, tenantId, and role claims. The token SHALL be signed with RS256 using the project's existing RSA key pair.

#### Scenario: JWT claims
- **WHEN** the system issues a JWT
- **THEN** the payload contains `sub` (userId), `tenantId`, `role`, `iat`, and `exp` claims

### Requirement: User data model extension
The `t_user` table SHALL be extended with: password_hash (VARCHAR 128), role (VARCHAR 32, default 'TENANT_ADMIN'), tenant_id (BIGINT FK to t_tenant), status (VARCHAR 16, default 'ACTIVE').

#### Scenario: Table structure
- **WHEN** Flyway migration V1 executes
- **THEN** the `t_user` table contains all extended columns with correct types and constraints

### Requirement: Bcrypt on elastic thread
Bcrypt password hashing and verification SHALL execute on `Schedulers.boundedElastic()` to avoid blocking Reactor event loop threads.

#### Scenario: Non-blocking verification
- **WHEN** a login request triggers bcrypt verification
- **THEN** the bcrypt computation runs on the boundedElastic scheduler, not the event loop thread