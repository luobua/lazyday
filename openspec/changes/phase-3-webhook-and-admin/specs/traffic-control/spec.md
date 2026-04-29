## ADDED Requirements

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