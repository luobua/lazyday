## ADDED Requirements

### Requirement: Tenant listing for platform admin

The system SHALL expose an admin API for listing all tenants with search, filter, and pagination.

#### Scenario: Paginated list

- **WHEN** an authenticated PLATFORM_ADMIN calls `GET /api/admin/v1/tenants?page=0&size=20`
- **THEN** the system returns a paginated response with `list` (array of tenant summaries), `total`, `total_pages`, `page`, `size`; each summary includes `id`, `name`, `email`, `status`, `plan_id`, `plan_name`, `created_time`

#### Scenario: Search by name or email

- **WHEN** PLATFORM_ADMIN calls `GET /api/admin/v1/tenants?keyword=acme`
- **THEN** the response only contains tenants whose `name` or contact email matches `%acme%` (case-insensitive)

#### Scenario: Filter by status

- **WHEN** PLATFORM_ADMIN calls `GET /api/admin/v1/tenants?status=SUSPENDED`
- **THEN** the response only contains tenants with `status='SUSPENDED'`

#### Scenario: Default sort

- **WHEN** no sort parameter is supplied
- **THEN** results are ordered by `created_time DESC`

### Requirement: Tenant detail for platform admin

The system SHALL expose an admin API returning detailed tenant information including current quota usage and AppKey count.

#### Scenario: Successful detail fetch

- **WHEN** PLATFORM_ADMIN calls `GET /api/admin/v1/tenants/{id}`
- **THEN** the response contains tenant base fields (`id`, `name`, `email`, `status`, `created_time`), bound plan summary (`plan_id`, `plan_name`, effective `qps_limit`, `daily_limit`, `monthly_limit`, `max_app_keys` after applying `custom_*` overrides), current `daily_usage`, current `monthly_usage`, `app_key_count` (count of AppKeys in `t_app_key` for this tenant regardless of status), and admin user emails (`tenant_admin_emails`)

#### Scenario: Tenant not found

- **WHEN** the requested tenant id does not exist
- **THEN** the system returns HTTP 404 with error_code `TENANT_NOT_FOUND`

### Requirement: Tenant suspension and resumption

The system SHALL allow PLATFORM_ADMIN to suspend and resume tenants; suspended tenants SHALL be blocked at the rate-limit layer.

#### Scenario: Suspend an active tenant

- **WHEN** PLATFORM_ADMIN submits `POST /api/admin/v1/tenants/{id}/suspend`
- **THEN** the system updates `t_tenant.status='SUSPENDED'`, publishes `TenantSuspendedEvent`, and returns the updated tenant summary

#### Scenario: Resume a suspended tenant

- **WHEN** PLATFORM_ADMIN submits `POST /api/admin/v1/tenants/{id}/resume`
- **THEN** the system updates `t_tenant.status='ACTIVE'`, publishes `TenantResumedEvent`, and returns the updated tenant summary

#### Scenario: Suspend already-suspended tenant

- **WHEN** the target tenant is already `SUSPENDED`
- **THEN** the system returns HTTP 200 with the existing record; no event is published; the operation is idempotent

#### Scenario: Resume already-active tenant

- **WHEN** the target tenant is already `ACTIVE`
- **THEN** the system returns HTTP 200 with the existing record; no event is published

#### Scenario: Suspended tenant request blocked at rate limiter

- **WHEN** a request bearing a JWT or AppKey for a tenant whose `t_tenant.status='SUSPENDED'` reaches `RateLimitWebFilter`
- **THEN** the filter returns HTTP 403 with error_code `TENANT_SUSPENDED` before evaluating any quota

#### Scenario: JWT not invalidated on suspension

- **WHEN** a tenant is suspended
- **THEN** existing JWTs SHALL NOT be invalidated server-side; the request is blocked solely by `RateLimitWebFilter`'s status check; authorized portal/admin endpoints continue to enforce their own auth

### Requirement: Platform overview metrics

The system SHALL expose an admin API returning platform-wide metrics for the operations dashboard.

#### Scenario: Overview metrics fetch

- **WHEN** PLATFORM_ADMIN calls `GET /api/admin/v1/overview`
- **THEN** the response contains: `total_tenants` (count of `t_tenant`), `active_tenants_7d` (count of distinct `tenant_id` in `t_call_log` where `request_time >= now() - 7 days`), `today_calls` (count of `t_call_log` where `request_time >= today`), `today_success_rate` (ratio of `status_code` ∈ [200,299] to total in today), `top_paths_today` (array of top 10 `(path, call_count)` ordered by call_count DESC for today)

#### Scenario: No data in window

- **WHEN** there are no call logs for today
- **THEN** `today_calls=0`, `today_success_rate=null`, `top_paths_today=[]`

#### Scenario: Metric query latency budget

- **WHEN** an overview request is served
- **THEN** the aggregation queries SHALL complete in under 1 second at scales of up to 10M `t_call_log` rows in the current day's partition; if the budget is exceeded, counter `lazyday.admin.overview.slow` SHALL be incremented at WARN level

### Requirement: Admin tenant management UI

The Admin Console SHALL render a tenant management page replacing the Phase 1 stub at `/tenants`.

#### Scenario: Tenant list page

- **WHEN** PLATFORM_ADMIN visits `/tenants`
- **THEN** the page shows a paginated table with columns `name`, `email`, `status`, `plan name`, `created time`, and an `actions` column with `详情`, `暂停` or `恢复`, `改套餐` buttons; the page provides a search input bound to `keyword` and a status filter dropdown

#### Scenario: Tenant detail drawer

- **WHEN** PLATFORM_ADMIN clicks `详情` on a tenant row
- **THEN** a drawer opens showing the tenant's metadata, effective quota, today's usage, this month's usage, AppKey count, and admin user emails

#### Scenario: Suspend confirmation dialog

- **WHEN** PLATFORM_ADMIN clicks `暂停` on an active tenant
- **THEN** a confirmation modal asks "确认暂停租户 {name}？暂停后该租户的所有 API 请求将返回 403"; on confirm, the page calls `POST /api/admin/v1/tenants/{id}/suspend` and refreshes the list

#### Scenario: Change plan from detail drawer

- **WHEN** PLATFORM_ADMIN clicks `改套餐` on a tenant row
- **THEN** a modal opens with a plan selector populated from `GET /api/admin/v1/plans` and optional custom override inputs; on confirm the page calls `PUT /api/admin/v1/tenants/{id}/quota` and refreshes