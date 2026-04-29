## ADDED Requirements

### Requirement: Webhook configuration management

The system SHALL allow each tenant to configure multiple webhook subscriptions, each subscribing to a subset of platform event types.

#### Scenario: Create webhook subscription

- **WHEN** an authenticated TENANT_ADMIN submits `POST /api/portal/v1/webhooks` with `name`, `url` (must start with `https://`), and `event_types` (non-empty subset of supported types)
- **THEN** the system creates a `t_webhook_config` row with `status='ACTIVE'`, generates a 32-byte random secret, AES-encrypts and stores it, and returns the configuration with the plaintext secret exactly once

#### Scenario: List webhook subscriptions

- **WHEN** TENANT_ADMIN calls `GET /api/portal/v1/webhooks`
- **THEN** the system returns all webhook configurations for the current tenant excluding the encrypted secret; each item includes `id`, `name`, `url`, `event_types`, `status`, and audit timestamps

#### Scenario: Update webhook subscription

- **WHEN** TENANT_ADMIN submits `PUT /api/portal/v1/webhooks/{id}` with new `name`, `url`, `event_types`, or `status`
- **THEN** the system updates the matching row scoped to the current tenant and returns the updated configuration

#### Scenario: Delete webhook subscription

- **WHEN** TENANT_ADMIN submits `DELETE /api/portal/v1/webhooks/{id}`
- **THEN** the system soft-deletes the configuration (status='DISABLED') and stops materializing new events for it; existing pending `t_webhook_event` rows for this config SHALL continue their retry lifecycle until terminal state

#### Scenario: Reject non-https URL

- **WHEN** TENANT_ADMIN submits a webhook with `url` that does not start with `https://`
- **THEN** the system returns HTTP 400 with error_code `WEBHOOK_INSECURE_URL` and does not create the configuration

#### Scenario: Reject loopback or private network URL

- **WHEN** TENANT_ADMIN submits a webhook whose URL host resolves to `localhost`, `127.0.0.0/8`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, or `169.254.0.0/16`
- **THEN** the system returns HTTP 400 with error_code `WEBHOOK_PRIVATE_NETWORK_URL`

#### Scenario: Cross-tenant isolation

- **WHEN** TENANT_ADMIN of tenant A submits `GET /api/portal/v1/webhooks/{id}` for an id belonging to tenant B
- **THEN** the system returns HTTP 404 with error_code `WEBHOOK_NOT_FOUND` (does not leak existence)

### Requirement: Secret rotation

The system SHALL allow tenants to rotate the HMAC signing secret of an existing webhook configuration.

#### Scenario: Rotate secret

- **WHEN** TENANT_ADMIN submits `POST /api/portal/v1/webhooks/{id}/rotate-secret`
- **THEN** the system generates a new 32-byte random secret, replaces the encrypted value, returns the new plaintext secret exactly once, and the next outbound delivery uses the new secret

#### Scenario: Old secret invalidated immediately

- **WHEN** the secret has been rotated
- **THEN** any in-flight outbound delivery SHALL use the new secret on retry; HMAC signatures generated with the old secret are no longer valid for verification on the receiver side

### Requirement: Domain event publishing

The system SHALL provide a `DomainEventPublisher` that emits domain events through a Reactor `Sinks.Many<DomainEvent>` configured as `multicast().onBackpressureBuffer(10000)`.

#### Scenario: Publish AppKey disabled

- **WHEN** an `AppKeyEntity.disable()` invocation completes successfully and its enclosing transaction commits
- **THEN** `DomainEventPublisher.publish` is invoked with an `AppKeyDisabledEvent` carrying `tenant_id`, `app_key_id`, `app_key_value`, and `disabled_time`

#### Scenario: Publish AppKey rotated

- **WHEN** AppKey rotation completes and the transaction commits
- **THEN** the system publishes `AppKeyRotatedEvent` with `tenant_id`, `app_key_id`, `rotated_time`, and `previous_secret_grace_until`

#### Scenario: Publish tenant suspended/resumed

- **WHEN** PLATFORM_ADMIN successfully suspends or resumes a tenant via Admin API
- **THEN** the system publishes `TenantSuspendedEvent` or `TenantResumedEvent` with `tenant_id` and `event_time`

#### Scenario: Publish quota exceeded

- **WHEN** `RateLimitWebFilter` rejects a request because a daily or monthly quota threshold was reached AND no `QuotaExceededEvent` has been published for the same `(tenant_id, period)` within the last 24 hours
- **THEN** the system publishes `QuotaExceededEvent` with `tenant_id`, `period` ∈ {`day`,`month`}, `limit`, and `event_time`

#### Scenario: Publish quota plan changed

- **WHEN** PLATFORM_ADMIN updates a tenant's `t_tenant_quota` row (plan binding or custom override fields)
- **THEN** the system publishes `QuotaPlanChangedEvent` with `tenant_id`, `previous_plan_id`, `new_plan_id`, and `change_time`

#### Scenario: Publish AFTER transaction commit

- **WHEN** the enclosing business transaction rolls back
- **THEN** no domain event SHALL be published; if the transaction commits, the event SHALL be published exactly once

#### Scenario: Backpressure buffer overflow

- **WHEN** the `Sinks.Many` buffer is full (10000 events backed up) and a new event is offered
- **THEN** the offer fails with `EmitResult.FAIL_OVERFLOW`, counter `lazyday.webhook.publish.dropped` SHALL be incremented at WARN level, and the calling business transaction SHALL NOT be affected

### Requirement: Event materialization

The system SHALL materialize each domain event into one `t_webhook_event` row per matching `t_webhook_config` (active and subscribed to the event type).

#### Scenario: Materialize for matching active configs

- **WHEN** `WebhookSubscriber` receives `AppKeyDisabledEvent` for tenant 7 and tenant 7 has 2 active webhook configs both subscribed to `appkey.disabled`
- **THEN** 2 rows are inserted into `t_webhook_event`, each with `status='pending'`, `next_retry_at=now()`, `retry_count=0`, and a snowflake-generated `id`

#### Scenario: Skip configs not subscribed

- **WHEN** an event of type X is received and a webhook config does not include X in its `event_types`
- **THEN** no row is materialized for that config

#### Scenario: Skip disabled configs

- **WHEN** a webhook config is in `status='DISABLED'`
- **THEN** no new event row is materialized for it regardless of subscription

#### Scenario: Materialization failure does not block subscriber

- **WHEN** the database is temporarily unreachable when materializing an event
- **THEN** the failure is logged at ERROR level with counter `lazyday.webhook.materialize.failed`; the event is dropped (no implicit retry); the subscriber continues processing subsequent events

### Requirement: Outbound delivery scheduling

The system SHALL run a `WebhookDispatcher` task that periodically pulls due events and delivers them, using PostgreSQL row-level locks to coordinate across replicas.

#### Scenario: Dispatch interval and batch size

- **WHEN** the application is running
- **THEN** `WebhookDispatcher.dispatch()` SHALL execute every `service.webhook.dispatchIntervalSeconds` (default 5) seconds and process at most 100 events per invocation

#### Scenario: Pull due events with row-level lock

- **WHEN** dispatch fires
- **THEN** the system executes `SELECT id FROM t_webhook_event WHERE status='pending' AND next_retry_at <= now() ORDER BY next_retry_at LIMIT 100 FOR UPDATE SKIP LOCKED`, then transitions matched rows to `status='delivering'`, `locked_at=now()`, `locked_by={instance_id}`

#### Scenario: Cross-replica exclusivity

- **WHEN** two Backend replicas execute dispatch at the same instant
- **THEN** each replica acquires a disjoint subset of pending rows due to `FOR UPDATE SKIP LOCKED`; no event is delivered twice

#### Scenario: Recover ghost-locked events

- **WHEN** an event row has been in `status='delivering'` with `locked_at < now() - 60s`
- **THEN** the next dispatch invocation SHALL reset its status to `pending` (preserving `retry_count` and `next_retry_at`), increment counter `lazyday.webhook.ghost_lock.recovered`, and include it in the next pull

### Requirement: HMAC-signed HTTP delivery

The system SHALL deliver each webhook event via HTTPS POST with HMAC-SHA256 signature headers, using a 10-second timeout.

#### Scenario: Delivery headers

- **WHEN** an event is delivered
- **THEN** the POST request carries headers `X-Lazyday-Event-Id`, `X-Lazyday-Event-Type`, `X-Lazyday-Timestamp` (epoch seconds), `X-Lazyday-Signature` (hex of `HMAC_SHA256(secret, timestamp + "." + body)`), `User-Agent: lazyday-webhook/1.0`, and `Content-Type: application/json`

#### Scenario: Successful delivery

- **WHEN** the receiver responds with HTTP status 2xx within 10 seconds
- **THEN** the event row is updated to `status='succeeded'`, `delivered_time=now()`, `last_http_status` is set, counter `lazyday.webhook.deliver.success` SHALL be incremented (tags: `event_type`)

#### Scenario: Failed delivery (non-2xx)

- **WHEN** the receiver responds with HTTP status outside 2xx within 10 seconds
- **THEN** the event row is updated to `status='failed'` with `last_http_status` and `last_response_excerpt` (truncated to 1024 chars); the dispatcher then evaluates retry policy

#### Scenario: Failed delivery (timeout or network error)

- **WHEN** the request times out (> 10 seconds) or the connection fails
- **THEN** the event row is updated to `status='failed'` with `last_error` describing the cause; counter `lazyday.webhook.deliver.failed` SHALL be incremented (tags: `event_type`, `reason` ∈ {timeout, connect_error, http_error})

#### Scenario: Latency observed

- **WHEN** any webhook delivery completes (success or failure)
- **THEN** timer `lazyday.webhook.deliver.latency` SHALL record the elapsed time

### Requirement: Retry policy with exponential backoff

The system SHALL retry failed deliveries up to `service.webhook.maxRetries` times (default 5) using the configured backoff sequence.

#### Scenario: Schedule next retry

- **WHEN** an event delivery fails and `retry_count < maxRetries`
- **THEN** the system increments `retry_count`, sets `next_retry_at = now() + service.webhook.backoffSequence[retry_count]` seconds (default sequence `60, 300, 1800, 7200, 21600`), transitions the row back to `status='pending'`, and clears `locked_at` / `locked_by`

#### Scenario: Permanent failure after max retries

- **WHEN** an event delivery fails and `retry_count >= maxRetries` after increment
- **THEN** the event row is updated to `status='permanent_failed'`, `delivered_time=now()`; the system publishes `WebhookPermanentFailedEvent` with `event_id`, `tenant_id`, `config_id`, `event_type`, `last_http_status`, `last_error`; counter `lazyday.webhook.permanent_failed` SHALL be incremented

### Requirement: First-attempt latency target

The system SHALL deliver each event for the first time within 10 seconds of publication under nominal load.

#### Scenario: First attempt within 10s

- **WHEN** a domain event is published, materialized, and the receiver responds within 1 second
- **THEN** the receiver's response timestamp SHALL be no more than 10 seconds after `t_webhook_event.created_time` (5s dispatch interval + headroom)

### Requirement: Test push

The system SHALL allow tenants to send a one-time test push to a configured webhook without enrolling it in retry or persistence flows.

#### Scenario: Test push success

- **WHEN** TENANT_ADMIN submits `POST /api/portal/v1/webhooks/{id}/test`
- **THEN** the system synchronously sends one POST with `event_type="webhook.test"` and a fixed sample payload using the same headers/signing as real deliveries; the response SHALL include `http_status`, `response_headers` (top 10), `response_body_excerpt` (truncated 1024 chars), and `latency_ms`

#### Scenario: Test push does not write t_webhook_event

- **WHEN** a test push is performed
- **THEN** no row is inserted into `t_webhook_event`; counters under `lazyday.webhook.deliver.*` SHALL NOT be incremented; a separate counter `lazyday.webhook.test.invoked` MAY be incremented

#### Scenario: Test push timeout

- **WHEN** the test push exceeds 10 seconds
- **THEN** the response returns `http_status=null`, `latency_ms=10000`, and `error_code=WEBHOOK_TEST_TIMEOUT`

### Requirement: Webhook data model

`t_webhook_config` SHALL contain: id (BIGINT IDENTITY PK), tenant_id (BIGINT NOT NULL), name (VARCHAR 100), url (VARCHAR 500), event_types (VARCHAR 500, comma-separated), secret_encrypted (VARCHAR 500), status (VARCHAR 20, default 'ACTIVE'), audit fields. Index on `tenant_id`.

`t_webhook_event` SHALL contain: id (BIGINT PK, snowflake), tenant_id (BIGINT NOT NULL), config_id (BIGINT NOT NULL FK), event_type (VARCHAR 50), payload (JSONB), status (VARCHAR 20), retry_count (INT NOT NULL DEFAULT 0), next_retry_at (TIMESTAMP NULL), locked_at (TIMESTAMP NULL), locked_by (VARCHAR 100 NULL), last_http_status (INT NULL), last_response_excerpt (VARCHAR 1024 NULL), last_error (VARCHAR 500 NULL), created_time (TIMESTAMP NOT NULL), delivered_time (TIMESTAMP NULL). Indexes: `(status, next_retry_at)`, `(tenant_id, created_time DESC)`.

#### Scenario: Tables exist after migration V4

- **WHEN** Flyway migration `V4__init_webhook.sql` executes
- **THEN** both tables exist with the specified columns, constraints, and indexes; no rows are seeded

#### Scenario: Snowflake id allocation

- **WHEN** a `t_webhook_event` row is inserted
- **THEN** its `id` is generated by the application-layer snowflake generator (not database identity), enabling client-side correlation before insert