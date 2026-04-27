## ADDED Requirements

### Requirement: AppKey creation
The system SHALL allow TENANT_ADMIN users to create AppKeys. Each AppKey SHALL have a unique `app_key` identifier and a `secret_key` encrypted with AES-256. The plaintext SecretKey SHALL be returned only once at creation time.

#### Scenario: Successful creation
- **WHEN** a TENANT_ADMIN submits `POST /api/portal/v1/credentials` with name and optional scopes
- **THEN** the system generates a unique app_key (format: `ak_` + random hex), generates a secret_key (format: `sk_` + random hex), encrypts the secret_key with AES using ServiceProperties.secretKey, stores the encrypted value, and returns the plaintext secret_key in the response (one-time only)

#### Scenario: AppKey scoped to tenant
- **WHEN** an AppKey is created
- **THEN** the AppKey record's tenant_id matches the creator's tenant_id from JWT context

### Requirement: AppKey listing
The system SHALL return all AppKeys belonging to the authenticated user's tenant. SecretKey SHALL always be masked.

#### Scenario: List own AppKeys
- **WHEN** a TENANT_ADMIN requests `GET /api/portal/v1/credentials`
- **THEN** the system returns all AppKeys where tenant_id matches the user's tenant, with secret_key displayed as masked (e.g., `sk_****xxxx`)

#### Scenario: Cannot list other tenant's AppKeys
- **WHEN** the query executes
- **THEN** the Repository method filters by tenant_id from TenantContext, never exposing cross-tenant data

### Requirement: AppKey disable and enable
The system SHALL allow TENANT_ADMIN to disable (status=DISABLED) and enable (status=ACTIVE) their own AppKeys.

#### Scenario: Disable AppKey
- **WHEN** a TENANT_ADMIN submits `PUT /api/portal/v1/credentials/{id}/disable`
- **THEN** the system verifies the AppKey belongs to the user's tenant, sets status to DISABLED, and returns HTTP 200

#### Scenario: Enable AppKey
- **WHEN** a TENANT_ADMIN submits `PUT /api/portal/v1/credentials/{id}/enable`
- **THEN** the system verifies tenant ownership, sets status to ACTIVE, and returns HTTP 200

#### Scenario: Cross-tenant disable attempt
- **WHEN** a TENANT_ADMIN attempts to disable an AppKey belonging to another tenant
- **THEN** the system returns HTTP 403 with error_code `FORBIDDEN_TENANT`

### Requirement: AppKey deletion
The system SHALL allow TENANT_ADMIN to delete their own AppKeys. Deletion SHALL be permanent.

#### Scenario: Delete AppKey
- **WHEN** a TENANT_ADMIN submits `DELETE /api/portal/v1/credentials/{id}`
- **THEN** the system verifies tenant ownership and deletes the AppKey record

### Requirement: SecretKey rotation with grace period
The system SHALL support SecretKey rotation with a 24-hour grace period during which both old and new keys are valid.

#### Scenario: Rotate SecretKey
- **WHEN** a TENANT_ADMIN submits `POST /api/portal/v1/credentials/{id}/rotate-secret`
- **THEN** the system generates a new secret_key, moves the current encrypted key to `secret_key_old`, sets `rotated_at` to now, sets `grace_period_end` to now + 24h, encrypts and stores the new key, and returns the new plaintext secret_key (one-time only)

#### Scenario: Grace period expiry
- **WHEN** the grace_period_end timestamp has passed
- **THEN** the old secret_key_old SHALL no longer be accepted for validation, and the field SHALL be cleared on next access

### Requirement: AppKey data model
The `t_app_key` table SHALL contain: id (BIGINT auto-increment PK), tenant_id (BIGINT FK), name (VARCHAR 64), app_key (VARCHAR 64 UNIQUE), secret_key_encrypted (VARCHAR 256), secret_key_old (VARCHAR 256 nullable), rotated_at (TIMESTAMP nullable), grace_period_end (TIMESTAMP nullable), status (VARCHAR 16 default 'ACTIVE'), scopes (VARCHAR 512 nullable), and audit fields.

#### Scenario: Table structure
- **WHEN** Flyway migration V1 executes
- **THEN** the `t_app_key` table exists with all specified columns, unique constraint on app_key, and foreign key to t_tenant