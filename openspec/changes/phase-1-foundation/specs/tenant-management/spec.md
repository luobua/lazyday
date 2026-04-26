## ADDED Requirements

### Requirement: Tenant registration
The system SHALL create a tenant and an admin user atomically when a developer registers. The new user SHALL have role `TENANT_ADMIN` and be associated with the newly created tenant via `tenant_id`.

#### Scenario: Successful registration
- **WHEN** a developer submits valid registration data (username, email, password, tenant_name)
- **THEN** the system creates a `t_tenant` record (status=ACTIVE) and a `t_user` record (role=TENANT_ADMIN, tenant_id=new tenant's id, password_hash=bcrypt of password) in a single transaction, and returns the user info with JWT cookies set

#### Scenario: Duplicate username
- **WHEN** a developer registers with a username that already exists
- **THEN** the system returns HTTP 409 with error_code `DUPLICATE_USERNAME`

#### Scenario: Duplicate email
- **WHEN** a developer registers with an email that already exists
- **THEN** the system returns HTTP 409 with error_code `DUPLICATE_EMAIL`

### Requirement: Tenant info retrieval
The system SHALL allow authenticated tenant admins to view their own tenant information. The query MUST be scoped to the user's `tenant_id` from JWT context.

#### Scenario: View own tenant
- **WHEN** an authenticated TENANT_ADMIN requests `GET /api/portal/v1/tenant`
- **THEN** the system returns the tenant record matching the user's tenant_id (name, status, plan_type, contact_email, created_at)

#### Scenario: Cannot view other tenants
- **WHEN** an authenticated TENANT_ADMIN attempts to access another tenant's data by manipulating request parameters
- **THEN** the system returns HTTP 403 with error_code `FORBIDDEN_TENANT`

### Requirement: Tenant info update
The system SHALL allow authenticated tenant admins to update their own tenant's basic information (name, contact_email).

#### Scenario: Update tenant name
- **WHEN** an authenticated TENANT_ADMIN submits `PUT /api/portal/v1/tenant` with a new name
- **THEN** the system updates the tenant record and returns the updated tenant info

### Requirement: Tenant data model
The `t_tenant` table SHALL contain: id (BIGINT auto-increment PK), name (VARCHAR 64), status (VARCHAR 16, default 'ACTIVE'), plan_type (VARCHAR 32, default 'FREE'), contact_email (VARCHAR 128), and audit fields (created_by, created_time, updated_by, updated_time).

#### Scenario: Table structure
- **WHEN** Flyway migration V1 executes
- **THEN** the `t_tenant` table exists with all specified columns and constraints