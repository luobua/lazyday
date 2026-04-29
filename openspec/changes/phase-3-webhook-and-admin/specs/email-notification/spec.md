## ADDED Requirements

### Requirement: Email infrastructure

The system SHALL ship a Spring Mail-based `EmailService` capable of sending HTML emails through an SMTP gateway (Aliyun DirectMail by default).

#### Scenario: SMTP configuration sources

- **WHEN** the application starts
- **THEN** SMTP credentials SHALL be loaded from environment variables `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`; the `From` address SHALL come from `service.email.from`

#### Scenario: Missing SMTP credentials in dev

- **WHEN** the application starts with one or more of the SMTP environment variables unset
- **THEN** the application SHALL still start; `EmailService` SHALL log WARN `email transport unconfigured, emails will be discarded`; calling `EmailService.send(...)` SHALL increment counter `lazyday.email.send.skipped` and return success without raising

#### Scenario: Async send does not block caller

- **WHEN** any subscriber invokes `EmailService.send(toAddress, subject, templateName, model)`
- **THEN** the method returns a `Mono<Void>` immediately; the actual SMTP I/O runs on `Schedulers.boundedElastic()` and SHALL NOT block the calling EventLoop thread

#### Scenario: Send latency observed

- **WHEN** any email send completes (success or failure)
- **THEN** timer `lazyday.email.send.latency` SHALL record the elapsed time and counter `lazyday.email.send.success` or `lazyday.email.send.failed` SHALL be incremented (tags: `template`)

### Requirement: Email templates

The system SHALL provide three Thymeleaf HTML templates under `resources/templates/email/`.

#### Scenario: Registration verification template

- **WHEN** `EmailService.send` is invoked with `template="registration-verify"` and model fields `userEmail`, `verifyUrl`, `expiresInHours`
- **THEN** the rendered email subject is `欢迎注册 Lazyday — 请验证邮箱`, and the body includes a clickable button linking to `verifyUrl` with text indicating the link expires in `expiresInHours` hours

#### Scenario: Quota exceeded template

- **WHEN** `EmailService.send` is invoked with `template="quota-exceeded"` and model fields `tenantName`, `period` ∈ {day, month}, `limit`, `usage`, `portalUrl`
- **THEN** the rendered email subject is `您的 Lazyday 配额已用尽`, and the body shows the period, limit, current usage, and a link to the portal quota page; `portalUrl` SHALL be an absolute URL composed by the subscriber from `service.domainHost` + `service.portalContextPathV1` + `/quota`

#### Scenario: Webhook permanent failed template

- **WHEN** `EmailService.send` is invoked with `template="webhook-permanent-failed"` and model fields `tenantName`, `webhookName`, `webhookUrl`, `eventType`, `eventId`, `lastHttpStatus`, `lastError`, `webhookConfigPortalUrl`
- **THEN** the rendered email subject is `Webhook 推送已永久失败 — {webhookName}`, and the body shows the failed event details and a deeplink to the webhook configuration page; `webhookConfigPortalUrl` SHALL be an absolute URL composed by the subscriber from `service.domainHost` + `service.portalContextPathV1` + `/webhooks?id={configId}`

### Requirement: Quota exceeded email triggering

The system SHALL send a quota-exceeded email at most once per tenant per 24 hours when the tenant first hits its daily or monthly quota.

#### Scenario: First quota rejection emits event

- **WHEN** `RateLimitWebFilter` rejects a request because the tenant reached its daily or monthly quota AND the publisher's dedup cache has no entry for `(tenantId, period)` within the last 24 hours
- **THEN** `DomainEventPublisher` publishes one `QuotaExceededEvent`; subsequent rejections within 24 hours for the same `(tenantId, period)` SHALL NOT publish another event

#### Scenario: Email sent to tenant admin user

- **WHEN** `QuotaExceededEmailSubscriber` receives `QuotaExceededEvent`
- **THEN** the subscriber resolves the tenant's TENANT_ADMIN user email from `t_user` (the user with role `TENANT_ADMIN` for that tenant) and invokes `EmailService.send(toEmail, ..., template="quota-exceeded", ...)` exactly once

#### Scenario: Multiple tenant admins

- **WHEN** a tenant has more than one TENANT_ADMIN user
- **THEN** the email SHALL be sent to all TENANT_ADMIN users of that tenant in a single send invocation (multiple recipients in `To` field)

#### Scenario: Tenant has no admin user

- **WHEN** a tenant unexpectedly has no TENANT_ADMIN user (data inconsistency)
- **THEN** the subscriber logs WARN `quota exceeded email skipped — no admin found`, increments counter `lazyday.email.send.skipped`, and does not raise

### Requirement: Webhook permanent-failed email triggering

The system SHALL send a webhook-permanent-failed email at most once per webhook config per 24 hours.

#### Scenario: Permanent failed event triggers email

- **WHEN** `WebhookPermanentFailedEmailSubscriber` receives `WebhookPermanentFailedEvent` AND the dedup cache has no entry for `(tenantId, configId)` within the last 24 hours
- **THEN** the subscriber sends one email with template `webhook-permanent-failed` to all TENANT_ADMIN users of the tenant

#### Scenario: Subsequent failures within 24h suppressed

- **WHEN** another `WebhookPermanentFailedEvent` arrives for the same `(tenantId, configId)` within 24 hours
- **THEN** no email is sent; counter `lazyday.email.send.deduped` SHALL be incremented (tags: `template=webhook-permanent-failed`)

### Requirement: Registration verification email

The system SHALL send a verification email to new tenant registrations when an SMTP transport is configured.

#### Scenario: Send on registration

- **WHEN** a new tenant registers via `POST /api/portal/v1/auth/register` and the registration transaction commits
- **THEN** the system sends a registration-verify email to the registered email address with a verification link valid for 24 hours

#### Scenario: Failed send does not roll back registration

- **WHEN** the email send fails (SMTP unreachable, authentication error)
- **THEN** the registration SHALL still be considered successful; the failure is logged at WARN level; counter `lazyday.email.send.failed` SHALL be incremented (tags: `template=registration-verify`)

#### Scenario: Email verification endpoint

- **WHEN** the recipient clicks the verification link `GET /api/portal/v1/auth/verify-email?token={token}` within 24 hours
- **THEN** the system marks the user's email as verified (`t_user.email_verified=true`) and returns a redirect to the portal login page; expired or unknown tokens return HTTP 400 with error_code `EMAIL_VERIFY_INVALID`; the endpoint MUST be reachable without an active session (no JWT cookie required) — both `JwtAuthWebFilter` and `RoleAuthorizationFilter` SHALL whitelist `/auth/verify-email` so that the recipient can verify from any browser