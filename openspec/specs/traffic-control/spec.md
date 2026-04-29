## Purpose

Define Backend in-process traffic control, request filtering, quota enforcement, and observability behavior.

## Requirements

### Requirement: Backend in-process rate limiting

The system SHALL enforce per-tenant QPS, daily, and monthly limits at the Backend WebFilter layer using `RateLimitWebFilter`. The filter SHALL apply to `/api/open/v1/**`, `/api/portal/v1/**`, and `/api/admin/v1/**`. The filter SHALL exclude all paths in the table below; `CallLogWebFilter` SHALL use the identical exclusion list.

**Excluded path prefixes (exhaustive)**:

| Prefix | Reason |
|---|---|
| `/api/portal/v1/auth/**` | Login / register / refresh / logout / me / csrf-token must not be rate-limited (would lock out legitimate users) |
| `/api/admin/v1/auth/**` | Same as above for Admin |
| `/api/open/v1/auth/**` | Reserved for Phase 2b Open API auth endpoints; same rationale |
| `/internal/v1/**` | Machine-to-machine; has its own global limit (D9) |
| `/actuator/**` | Health/metrics scrapes must always succeed |
| `/error` | Spring's default error path |

#### Scenario: QPS exceeded

- **WHEN** a tenant exceeds its effective `qps_limit` within a one-second window
- **THEN** the system returns HTTP 429 with error_code `RATE_LIMIT_EXCEEDED` and response headers `X-RateLimit-Limit`, `X-RateLimit-Remaining=0`, `X-RateLimit-Reset` (epoch milliseconds), and `Retry-After` (seconds)

#### Scenario: Daily quota exceeded (single-replica precision)

- **WHEN** a tenant on a single Backend replica makes requests that bring its in-process `LongAdder` for the current day to the effective `daily_limit`
- **THEN** the next request within the same day returns HTTP 429 with error_code `QUOTA_DAILY_EXCEEDED` with no overshoot

#### Scenario: Monthly quota exceeded (single-replica precision)

- **WHEN** a tenant on a single Backend replica reaches its effective `monthly_limit`
- **THEN** subsequent requests within the same month return HTTP 429 with error_code `QUOTA_MONTHLY_EXCEEDED`

#### Scenario: Multi-replica overshoot envelope (Phase 2a accepted imprecision)

- **WHEN** Backend is deployed with N > 1 replicas and each replica enforces quota independently
- **THEN** the platform-wide pass-through count for any tenant in a day MAY exceed `daily_limit` by up to `(N − 1) × daily_limit` in pathological cases
- **AND** this imprecision SHALL be eliminated in Phase 2b once Edge gateway with Redis counter becomes the primary rate limiter

#### Scenario: Counter warm-up on cache miss

- **WHEN** a request arrives and the tenant has no in-process `LongAdder` for the current day (first access, or after eviction, or after process restart)
- **THEN** the system SHALL execute one `SELECT COUNT(*) FROM t_call_log WHERE tenant_id = ? AND request_time >= ?` to seed the adder before evaluating the request, with a target cold-start latency of < 200ms even at Enterprise scale

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

#### Scenario: Bucket rebuilt after eviction

- **WHEN** a tenant's bucket has been evicted from the cache (due to LRU pressure or 30-minute idle expiration) and a new request arrives
- **THEN** the system SHALL rebuild the bucket by reading the tenant's effective quota from `t_tenant_quota` and resume rate limiting with a full token bucket; the request itself SHALL be evaluated against the freshly built bucket

### Requirement: Tenant context resolution

The filter SHALL resolve tenant context from request headers/JWT in this order: `X-Tenant-Id` header (when set by an upstream gateway) → JWT TenantContext → none.

#### Scenario: Header overrides JWT

- **WHEN** a request carries both `X-Tenant-Id: 5` and a JWT for tenant 7
- **THEN** the filter uses tenant 5

#### Scenario: JWT fallback

- **WHEN** a request has no `X-Tenant-Id` header but has a valid JWT
- **THEN** the filter uses the tenant from JWT context

### Requirement: Observability metrics

The system SHALL emit the following Micrometer metrics through the application's `MeterRegistry`. Metric names SHALL be stable across patch releases; tag dimensions are non-normative.

#### Scenario: Rate limit decision counters

- **WHEN** `RateLimitWebFilter` allows a request
- **THEN** counter `lazyday.ratelimit.allowed` SHALL be incremented (tags: `tenantId`)
- **WHEN** `RateLimitWebFilter` rejects a request with HTTP 429
- **THEN** counter `lazyday.ratelimit.rejected` SHALL be incremented (tags: `tenantId`, `reason` ∈ {qps, daily, monthly})

#### Scenario: Rate limit filter latency

- **WHEN** any request traverses `RateLimitWebFilter`
- **THEN** timer `lazyday.ratelimit.latency` SHALL record the filter's own processing time (excluding downstream chain)

#### Scenario: Quota counter cache health

- **WHEN** the `LongAdder` for a `(tenantId, day|month)` key is created via DB warm-up
- **THEN** counter `lazyday.quota.counter.warmup` SHALL be incremented (tags: `period` ∈ {day, month})
- **WHEN** the warm-up SQL exceeds 200ms
- **THEN** counter `lazyday.quota.counter.warmup.slow` SHALL be incremented

#### Scenario: Call log writer health

- **WHEN** a call log entry is successfully written to the database
- **THEN** counter `lazyday.calllog.write.success` SHALL be incremented
- **WHEN** a call log write fails (already covered by `lazyday.calllog.write.failed`) or is shed (`lazyday.calllog.write.shed`)
- **THEN** the corresponding counter SHALL be incremented

#### Scenario: Partition scheduler health

- **WHEN** `PartitionScheduler` successfully creates a partition
- **THEN** counter `lazyday.partition.scheduler.created` SHALL be incremented
- **WHEN** `PartitionScheduler` fails to create a partition
- **THEN** counter `lazyday.partition.scheduler.failed` SHALL be incremented at WARN level

### Requirement: Edge integration boundary

The `RateLimitWebFilter` SHALL be designed so that its activation is independent of the future Edge gateway. After Phase 2b's Edge `RateLimitGlobalFilter` is online, this Backend filter SHALL continue to operate as a fallback against direct-to-Backend traffic.

#### Scenario: Behavior unchanged when Edge is absent

- **WHEN** no Edge gateway is deployed and clients call Backend directly
- **THEN** rate limiting works correctly using Backend-resolved tenant context

#### Scenario: Behavior unchanged when Edge is present

- **WHEN** Edge is deployed and forwards `X-Tenant-Id` to Backend
- **THEN** Backend still enforces the same limits using the forwarded tenant id (acting as a defense-in-depth layer)

### Requirement: Quota exceeded event publication

The `RateLimitWebFilter` SHALL publish a `QuotaExceededEvent` to the domain event bus the first time a tenant hits its daily or monthly quota within a 24h dedup window per `(tenant, period)`.

#### Scenario: First daily-quota rejection publishes event

- **WHEN** the filter rejects a request with error_code `QUOTA_DAILY_EXCEEDED` AND the in-process dedup cache has no entry for `(tenant_id, period=day)`
- **THEN** the filter calls `DomainEventPublisher.publish(QuotaExceededEvent)` with payload `{tenantId, period=day, limit=effectiveDailyLimit, eventTime=now()}` and stores the dedup key with TTL=24h

#### Scenario: First monthly-quota rejection publishes event

- **WHEN** the filter rejects a request with error_code `QUOTA_MONTHLY_EXCEEDED` AND the dedup cache has no entry for `(tenant_id, period=month)`
- **THEN** the filter publishes `QuotaExceededEvent` with `period=month`

#### Scenario: Subsequent rejections within window suppressed

- **WHEN** the filter rejects subsequent requests for the same `(tenant_id, period)` within the 24h window
- **THEN** no event is published; the rejection itself still returns HTTP 429 normally

#### Scenario: QPS rejection does not publish event

- **WHEN** the filter rejects a request with error_code `RATE_LIMIT_EXCEEDED` (per-second QPS bucket exhausted)
- **THEN** no `QuotaExceededEvent` SHALL be published — QPS spikes are too noisy and do not represent meaningful business signal

#### Scenario: Dedup cache memory bounded

- **WHEN** the dedup cache size reaches `service.ratelimit.quotaEventDedupMaxSize` (default 10000)
- **THEN** the least-recently-used entry SHALL be evicted; a tenant whose entry is evicted MAY trigger another email if it is rate-limited again

### Requirement: Suspended tenant blocked at rate-limit layer

The `RateLimitWebFilter` SHALL reject all requests from tenants whose `t_tenant.status='SUSPENDED'` before evaluating quota or QPS limits.

#### Scenario: Suspended tenant rejected

- **WHEN** a request resolves to a tenant whose `t_tenant.status='SUSPENDED'`
- **THEN** the filter returns HTTP 403 with error_code `TENANT_SUSPENDED` and response body `{"code":40300,"error_code":"TENANT_SUSPENDED","message":"Tenant has been suspended","data":null}`; counter `lazyday.ratelimit.rejected` SHALL be incremented (tags: `tenantId`, `reason=suspended`)

#### Scenario: Active tenant unaffected

- **WHEN** a request resolves to a tenant whose `t_tenant.status='ACTIVE'`
- **THEN** the filter proceeds to the existing QPS / quota logic without change

#### Scenario: Tenant status cached briefly

- **WHEN** a tenant's status changes from ACTIVE to SUSPENDED
- **THEN** the change SHALL take effect on `RateLimitWebFilter` within 30 seconds without a Backend restart

#### Scenario: Status check uses tenant context resolver

- **WHEN** the filter has resolved tenant context (via `X-Tenant-Id` header or JWT)
- **THEN** the SUSPENDED check SHALL execute before QPS/quota; if no tenant context can be resolved, the SUSPENDED check is skipped (request passes through to downstream auth, consistent with the existing "filter ignores requests without tenant context" rule)
