## ADDED Requirements

### Requirement: Backend in-process rate limiting

The system SHALL enforce per-tenant QPS, daily, and monthly limits at the Backend WebFilter layer using `RateLimitWebFilter`. The filter SHALL apply to `/api/open/v1/**`, `/api/portal/v1/**`, and `/api/admin/v1/**`, excluding `auth` subpaths and `/actuator/**` and `/internal/**`.

#### Scenario: QPS exceeded

- **WHEN** a tenant exceeds its effective `qps_limit` within a one-second window
- **THEN** the system returns HTTP 429 with error_code `RATE_LIMIT_EXCEEDED` and response headers `X-RateLimit-Limit`, `X-RateLimit-Remaining=0`, `X-RateLimit-Reset` (epoch milliseconds), and `Retry-After` (seconds)

#### Scenario: Daily quota exceeded

- **WHEN** a tenant's count of successful requests in `t_call_log` for the current day reaches the effective `daily_limit`
- **THEN** subsequent requests within the same day return HTTP 429 with error_code `QUOTA_DAILY_EXCEEDED`

#### Scenario: Monthly quota exceeded

- **WHEN** a tenant's count of successful requests for the current month reaches the effective `monthly_limit`
- **THEN** subsequent requests within the same month return HTTP 429 with error_code `QUOTA_MONTHLY_EXCEEDED`

#### Scenario: Plan or override change takes effect

- **WHEN** PLATFORM_ADMIN updates a plan's limits or a tenant's custom override
- **THEN** the new effective quota SHALL apply to that tenant within 30 seconds without requiring a Backend restart

#### Scenario: Auth endpoints bypass rate limit

- **WHEN** an unauthenticated request hits `/api/portal/v1/auth/login` or `/api/admin/v1/auth/login`
- **THEN** `RateLimitWebFilter` SHALL NOT apply

#### Scenario: Filter ignores requests without tenant context

- **WHEN** a request reaches `RateLimitWebFilter` without a resolvable tenant (no JWT and no `X-Tenant-Id` header)
- **THEN** the filter SHALL pass through without enforcing limits, leaving authentication failures to be handled downstream

### Requirement: Bucket lifecycle and memory bounds

The system SHALL maintain per-tenant token buckets in a bounded in-memory cache.

#### Scenario: Bucket cache bounded by tenant count

- **WHEN** the number of active tenants exceeds 10000 (cache max size)
- **THEN** the least-recently-used bucket SHALL be evicted

#### Scenario: Idle bucket expiration

- **WHEN** a tenant has not made a request for 30 minutes
- **THEN** its bucket entry MAY be evicted from the cache; the next request rebuilds it from `t_tenant_quota`

### Requirement: Tenant context resolution

The filter SHALL resolve tenant context from request headers/JWT in this order: `X-Tenant-Id` header (when set by an upstream gateway) → JWT TenantContext → none.

#### Scenario: Header overrides JWT

- **WHEN** a request carries both `X-Tenant-Id: 5` and a JWT for tenant 7
- **THEN** the filter uses tenant 5

#### Scenario: JWT fallback

- **WHEN** a request has no `X-Tenant-Id` header but has a valid JWT
- **THEN** the filter uses the tenant from JWT context

### Requirement: Edge integration boundary

The `RateLimitWebFilter` SHALL be designed so that its activation is independent of the future Edge gateway. After Phase 2b's Edge `RateLimitGlobalFilter` is online, this Backend filter SHALL continue to operate as a fallback against direct-to-Backend traffic.

#### Scenario: Behavior unchanged when Edge is absent

- **WHEN** no Edge gateway is deployed and clients call Backend directly
- **THEN** rate limiting works correctly using Backend-resolved tenant context

#### Scenario: Behavior unchanged when Edge is present

- **WHEN** Edge is deployed and forwards `X-Tenant-Id` to Backend
- **THEN** Backend still enforces the same limits using the forwarded tenant id (acting as a defense-in-depth layer)