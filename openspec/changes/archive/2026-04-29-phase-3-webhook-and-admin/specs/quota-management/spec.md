## ADDED Requirements

### Requirement: Plan binding change publishes domain event

The system SHALL publish a `QuotaPlanChangedEvent` whenever a tenant's `t_tenant_quota` row changes plan binding or any custom override field.

#### Scenario: Bind plan triggers event

- **WHEN** PLATFORM_ADMIN successfully calls `PUT /api/admin/v1/tenants/{tenantId}/quota` with a different `planId` than the current binding
- **THEN** after the underlying transaction commits, the system publishes one `QuotaPlanChangedEvent` carrying `tenant_id`, `previous_plan_id`, `new_plan_id`, `change_time`

#### Scenario: Override change triggers event

- **WHEN** PLATFORM_ADMIN calls `PUT /api/admin/v1/tenants/{tenantId}/quota` keeping the same `planId` but changing one of `customQpsLimit`, `customDailyLimit`, `customMonthlyLimit`, `customMaxAppKeys`
- **THEN** the system publishes `QuotaPlanChangedEvent` with `previous_plan_id == new_plan_id` and the change is still surfaced to webhook subscribers

#### Scenario: Idempotent update suppresses event

- **WHEN** the request body is identical to the current `t_tenant_quota` row state (no field actually changes)
- **THEN** no domain event SHALL be published

### Requirement: Quota exceeded email integration

The system SHALL trigger a quota-exceeded email to the tenant's admin user the first time a daily or monthly quota is hit, deduplicated to once per 24 hours per `(tenant, period)` pair.

#### Scenario: Email triggered on first daily exhaustion

- **WHEN** a tenant's effective `daily_limit` is reached and `RateLimitWebFilter` returns HTTP 429 with error_code `QUOTA_DAILY_EXCEEDED`
- **AND** no `QuotaExceededEvent` has been published for `(tenant_id, period=day)` within the last 24 hours
- **THEN** the system publishes `QuotaExceededEvent` and `EmailService` sends the `quota-exceeded` template to all TENANT_ADMIN users of the tenant

#### Scenario: Email triggered on first monthly exhaustion

- **WHEN** a tenant's effective `monthly_limit` is reached
- **AND** no `QuotaExceededEvent` has been published for `(tenant_id, period=month)` within the last 24 hours
- **THEN** the same flow as the daily scenario triggers, with `period=month` in the event payload and email model

#### Scenario: Subsequent rejections within 24h do not email

- **WHEN** a tenant continues to be rate-limited within the same 24h dedup window for the same period
- **THEN** no additional email SHALL be sent; counter `lazyday.email.send.deduped` SHALL be incremented (tags: `template=quota-exceeded`)

#### Scenario: Daily and monthly are independent

- **WHEN** both daily and monthly quotas are exceeded on the same day for the same tenant
- **THEN** two separate emails MAY be sent (one per period), each subject to its own 24h dedup