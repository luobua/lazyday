## Purpose

Define quota plan, tenant quota binding, effective quota calculation, and quota management API behavior.

## Requirements

### Requirement: Quota plan templates

The system SHALL provide reusable quota plan templates that define default `qps_limit`, `daily_limit`, `monthly_limit`, and `max_app_keys` values. The system SHALL ship with three seed plans: `Free`, `Pro`, and `Enterprise`.

#### Scenario: Seed plans available after migration

- **WHEN** Flyway migration V3 executes successfully
- **THEN** `t_quota_plan` contains exactly three records named `Free`, `Pro`, `Enterprise`, all with `status='ACTIVE'`

#### Scenario: List plans

- **WHEN** an authenticated PLATFORM_ADMIN calls `GET /api/admin/v1/plans`
- **THEN** the system returns all plans (active and disabled) with their full configuration

#### Scenario: Create new plan

- **WHEN** PLATFORM_ADMIN submits `POST /api/admin/v1/plans` with valid `name`, `qps_limit > 0`, `daily_limit > 0`, `monthly_limit > 0`, `max_app_keys`
- **THEN** the system creates a new `t_quota_plan` row with `status='ACTIVE'` and returns its full record

#### Scenario: Update plan

- **WHEN** PLATFORM_ADMIN submits `PUT /api/admin/v1/plans/{id}` with new limits
- **THEN** the system updates the plan record and the change SHALL be visible to subsequent quota lookups within 30 seconds

#### Scenario: Disable plan currently in use

- **WHEN** PLATFORM_ADMIN submits `DELETE /api/admin/v1/plans/{id}` for a plan that is bound by at least one tenant
- **THEN** the system returns HTTP 409 with error_code `PLAN_IN_USE` and does not modify the plan

#### Scenario: Disable unused plan

- **WHEN** PLATFORM_ADMIN submits `DELETE /api/admin/v1/plans/{id}` for a plan with no tenant bindings
- **THEN** the system marks the plan `status='DISABLED'` (soft delete) and returns the updated record

### Requirement: Default plan binding on tenant lifecycle

The system SHALL guarantee that every tenant has exactly one `t_tenant_quota` row at all times. This invariant SHALL hold for tenants created before V3 migration AND for tenants registered after V3 migration is applied.

#### Scenario: Backfill existing tenants on migration

- **WHEN** Flyway migration V3 executes against a database that already contains tenants from Phase 1
- **THEN** the migration creates one `t_tenant_quota` row per existing tenant binding it to the seed `Free` plan, with all `custom_*` fields null

#### Scenario: New tenant registration auto-binds Free plan

- **WHEN** a developer registers a new tenant via `POST /api/portal/v1/auth/register`
- **THEN** the registration transaction SHALL create the `t_tenant_quota` row binding the new tenant to the `Free` plan atomically with the `t_tenant` and `t_user` inserts; if any of the three inserts fails the entire transaction SHALL roll back

#### Scenario: Plan binding survives tenant suspension

- **WHEN** a PLATFORM_ADMIN suspends a tenant (status change on `t_tenant`)
- **THEN** the `t_tenant_quota` row SHALL remain unchanged so quota state is preserved across suspend/resume cycles

### Requirement: Tenant quota binding and override

The system SHALL bind exactly one quota plan to each tenant via `t_tenant_quota`. The system SHALL allow PLATFORM_ADMIN to override individual limit fields per tenant via `custom_*` columns; non-null custom values take precedence over plan defaults.

#### Scenario: Bind a plan to tenant

- **WHEN** PLATFORM_ADMIN submits `PUT /api/admin/v1/tenants/{tenantId}/quota` with `planId=2`
- **THEN** the system upserts `t_tenant_quota` for that tenant with `plan_id=2` and all `custom_*` set to null

#### Scenario: Override individual quota fields

- **WHEN** PLATFORM_ADMIN submits `PUT /api/admin/v1/tenants/{tenantId}/quota` with `planId=2` and `customQpsLimit=200`
- **THEN** the system stores `plan_id=2` and `custom_qps_limit=200` while leaving other custom fields null

#### Scenario: Effective quota calculation

- **WHEN** the system calculates effective quota for a tenant
- **THEN** for each limit field it returns `COALESCE(custom_field, plan.field)`

#### Scenario: Override is removed by setting to null

- **WHEN** PLATFORM_ADMIN updates a tenant's `custom_qps_limit` to null
- **THEN** the effective qps reverts to the plan's default `qps_limit`

### Requirement: Effective quota query

The system SHALL expose an authenticated query for the current tenant's effective quota and a server-side internal query for any tenant.

#### Scenario: Tenant queries own quota

- **WHEN** an authenticated TENANT_ADMIN calls `GET /api/portal/v1/quota`
- **THEN** the system returns the tenant's effective `qps_limit`, `daily_limit`, `monthly_limit`, `max_app_keys`, plan name, current-day usage count, and current-month usage count

#### Scenario: Tenant cannot query other tenants' quota

- **WHEN** an authenticated TENANT_ADMIN attempts to query quota by manipulating tenant_id parameters
- **THEN** the system ignores the parameter and uses the tenant_id from JWT context

#### Scenario: Internal effective quota query

- **WHEN** a service calls `GET /internal/v1/quota/effective?tenantId={id}` with a valid `X-Internal-Api-Key` header
- **THEN** the system returns the effective quota for the requested tenant

#### Scenario: Internal query without valid api key

- **WHEN** a service calls `GET /internal/v1/quota/effective` without `X-Internal-Api-Key` or with an incorrect value
- **THEN** the system returns HTTP 403 with error_code `INTERNAL_AUTH_FAILED` and writes one INFO entry to the `lazyday.internal.audit` logger with caller IP and the failed result

#### Scenario: Application fails to start when internal api key is unset or too short

- **WHEN** the Backend application starts with `service.internalApiKey` empty, missing, or shorter than 32 characters
- **THEN** the application SHALL fail to start and log the configuration error; no listening port is opened

#### Scenario: Internal API global rate limit

- **WHEN** the global request rate to `/internal/v1/**` exceeds 100 QPS on a single Backend replica
- **THEN** excess requests return HTTP 429 with error_code `INTERNAL_RATE_LIMITED`, regardless of which tenantId they query

#### Scenario: Successful internal call audited

- **WHEN** a service makes a successful call to `/internal/v1/**` with a valid api key
- **THEN** one INFO entry is written to the `lazyday.internal.audit` logger containing `{timestamp, caller_ip, path, tenantId, result=ok}`

### Requirement: Quota data model

The `t_quota_plan` table SHALL contain: id (BIGINT IDENTITY PK), name (VARCHAR 50), qps_limit (INT), daily_limit (BIGINT), monthly_limit (BIGINT), max_app_keys (INT, -1 means unlimited), status (VARCHAR 20, default 'ACTIVE'), audit fields.

The `t_tenant_quota` table SHALL contain: id (BIGINT IDENTITY PK), tenant_id (BIGINT UNIQUE), plan_id (BIGINT), custom_qps_limit (INT NULL), custom_daily_limit (BIGINT NULL), custom_monthly_limit (BIGINT NULL), custom_max_app_keys (INT NULL), audit fields. An index on `tenant_id` SHALL exist.

#### Scenario: Tables exist after migration

- **WHEN** Flyway migration V3 executes
- **THEN** both tables exist with the specified columns, constraints, and the `idx_tenant_quota_tenant` index
