## ADDED Requirements

### Requirement: JWT authentication filter
The system SHALL intercept all requests to `/api/portal/**` and `/api/admin/**`, extract the JWT from the `access_token` cookie, validate the signature and expiry, and inject userId, tenantId, and role into the Reactor Context as `TenantContext`.

#### Scenario: Valid JWT
- **WHEN** a request to `/api/portal/v1/tenant` carries a valid access_token cookie
- **THEN** the filter extracts claims, populates TenantContext in Reactor Context, and passes the request to the next filter

#### Scenario: Missing JWT
- **WHEN** a request to a protected path carries no access_token cookie
- **THEN** the filter returns HTTP 401 with error_code `UNAUTHORIZED`

#### Scenario: Expired JWT
- **WHEN** a request carries an expired access_token
- **THEN** the filter returns HTTP 401 with error_code `TOKEN_EXPIRED`

#### Scenario: Public paths excluded
- **WHEN** a request targets `/api/portal/v1/auth/login`, `/auth/register`, or `/auth/csrf-token`
- **THEN** the filter passes the request without JWT validation

### Requirement: Role-based authorization (vertical)
The system SHALL enforce that `/api/admin/**` endpoints require role `PLATFORM_ADMIN` and `/api/portal/**` endpoints require role `TENANT_ADMIN`. Role mismatch SHALL return HTTP 403.

#### Scenario: TENANT_ADMIN accesses admin endpoint
- **WHEN** a user with role TENANT_ADMIN requests `GET /api/admin/v1/tenants`
- **THEN** the system returns HTTP 403 with error_code `FORBIDDEN_ROLE`

#### Scenario: PLATFORM_ADMIN accesses admin endpoint
- **WHEN** a user with role PLATFORM_ADMIN requests `GET /api/admin/v1/tenants`
- **THEN** the request proceeds normally

#### Scenario: PLATFORM_ADMIN accesses portal endpoint
- **WHEN** a user with role PLATFORM_ADMIN requests `GET /api/portal/v1/tenant`
- **THEN** the system returns HTTP 403 with error_code `FORBIDDEN_ROLE` (admin uses admin endpoints)

### Requirement: Tenant data isolation (horizontal)
The system SHALL ensure that all data-access operations in Portal APIs are scoped to the authenticated user's tenant_id. A user SHALL never be able to read, modify, or delete resources belonging to another tenant.

#### Scenario: Repository enforces tenant_id
- **WHEN** any Portal Repository method executes a query
- **THEN** the query includes a `WHERE tenant_id = ?` condition using the value from TenantContext

#### Scenario: ID-based access to foreign resource
- **WHEN** a TENANT_ADMIN of tenant A requests `PUT /api/portal/v1/credentials/{id}/disable` where {id} belongs to tenant B
- **THEN** the system returns HTTP 403 with error_code `FORBIDDEN_TENANT`

#### Scenario: URL parameter manipulation
- **WHEN** a TENANT_ADMIN modifies URL path parameters or query parameters to reference resources of another tenant
- **THEN** the system returns HTTP 403 (the Repository query finds no matching record for the user's tenant_id)

### Requirement: CSRF protection
The system SHALL implement double-submit cookie CSRF protection for all state-changing requests (POST, PUT, DELETE) to Portal and Admin endpoints.

#### Scenario: CSRF token acquisition
- **WHEN** a client requests `GET /api/portal/v1/auth/csrf-token`
- **THEN** the system generates a random CSRF token, sets it as a cookie (`csrf_token`; NOT HttpOnly so JavaScript can read it), and returns the token in the response body

#### Scenario: Valid CSRF token
- **WHEN** a POST request includes `X-CSRF-Token` header matching the `csrf_token` cookie value
- **THEN** the request proceeds normally

#### Scenario: Missing CSRF token
- **WHEN** a POST request to a protected endpoint omits the `X-CSRF-Token` header
- **THEN** the system returns HTTP 403 with error_code `CSRF_TOKEN_MISSING`

#### Scenario: Invalid CSRF token
- **WHEN** a POST request includes an `X-CSRF-Token` header that does not match the cookie
- **THEN** the system returns HTTP 403 with error_code `CSRF_TOKEN_INVALID`

#### Scenario: GET requests exempt
- **WHEN** a GET request is made without CSRF token
- **THEN** the request proceeds normally (CSRF only applies to state-changing methods)

#### Scenario: Auth endpoints exempt
- **WHEN** a POST to `/auth/login` or `/auth/register` is made without CSRF token
- **THEN** the request proceeds normally (login/register are pre-authentication)

### Requirement: Filter chain ordering
The security filters SHALL execute in this order: JwtAuthWebFilter → RoleAuthorizationFilter → CsrfProtectionFilter. Each filter can short-circuit with an error response.

#### Scenario: Filter execution order
- **WHEN** a request arrives at a protected endpoint
- **THEN** JWT authentication runs first, then role authorization, then CSRF validation, and finally the handler