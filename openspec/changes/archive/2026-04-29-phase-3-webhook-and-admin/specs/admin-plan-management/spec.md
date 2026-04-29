## ADDED Requirements

### Requirement: Plan binding count surfaced in admin list

The system SHALL augment the existing `GET /api/admin/v1/plans` response with each plan's current binding count for use by the admin UI.

#### Scenario: List plans returns binding count

- **WHEN** PLATFORM_ADMIN calls `GET /api/admin/v1/plans`
- **THEN** each plan in the response includes a `binding_count` field equal to the number of `t_tenant_quota` rows whose `plan_id` matches the plan id

#### Scenario: Binding count is computed live

- **WHEN** a tenant's `t_tenant_quota.plan_id` changes between two consecutive calls
- **THEN** the second call reflects the new counts; no caching layer SHALL keep stale counts beyond the request lifetime

#### Scenario: Binding count for disabled plan

- **WHEN** a plan has `status='DISABLED'`
- **THEN** the response still includes the plan with its current `binding_count` (which MUST be 0 if `delete plan currently in use` rejection contract from Phase 2a holds)

### Requirement: Admin plan management UI

The Admin Console SHALL render a plan management page replacing the Phase 1 stub at `/plans`.

#### Scenario: Plan list page

- **WHEN** PLATFORM_ADMIN visits `/plans`
- **THEN** the page shows a table with columns `name`, `qps_limit`, `daily_limit`, `monthly_limit`, `max_app_keys`, `binding_count`, `status`, `created_time`, and action buttons `编辑`, `禁用`

#### Scenario: Create plan modal

- **WHEN** PLATFORM_ADMIN clicks `新建套餐`
- **THEN** a modal opens with form fields `name` (required), `qps_limit` (required, > 0), `daily_limit` (required, > 0), `monthly_limit` (required, > 0), `max_app_keys` (required, integer, -1 means unlimited); on submit the page calls `POST /api/admin/v1/plans` and refreshes the list

#### Scenario: Edit plan modal

- **WHEN** PLATFORM_ADMIN clicks `编辑` on a plan row
- **THEN** a modal opens prefilled with the plan's current values; on submit the page calls `PUT /api/admin/v1/plans/{id}` and refreshes the list

#### Scenario: Disable plan with bindings

- **WHEN** PLATFORM_ADMIN clicks `禁用` on a plan with `binding_count > 0`
- **THEN** the page calls `DELETE /api/admin/v1/plans/{id}`; on the expected HTTP 409 with `error_code=PLAN_IN_USE`, the page displays an error toast `该套餐正在被 {binding_count} 个租户使用，无法禁用`

#### Scenario: Disable unused plan

- **WHEN** PLATFORM_ADMIN clicks `禁用` on a plan with `binding_count = 0`
- **THEN** a confirmation modal asks "确认禁用套餐 {name}？禁用后将不能再绑定到新租户"; on confirm, the page calls `DELETE /api/admin/v1/plans/{id}` and refreshes