## ADDED Requirements

### Requirement: Unified API response format
All API endpoints SHALL return responses wrapped in a standard `ApiResponse<T>` structure containing: `code` (int, 0=success), `error_code` (string, null on success), `message` (string, human-readable), `data` (T, null on error), and `request_id` (string, unique per request).

#### Scenario: Successful response
- **WHEN** an API call succeeds
- **THEN** the response body is `{ "code": 0, "error_code": null, "message": "success", "data": {...}, "request_id": "uuid" }`

#### Scenario: Error response
- **WHEN** an API call fails with a business error
- **THEN** the response body is `{ "code": 40101, "error_code": "INVALID_CREDENTIALS", "message": "用户名或密码错误", "data": null, "request_id": "uuid" }`

### Requirement: Global exception handling
The system SHALL catch all unhandled exceptions via `@ControllerAdvice` and map them to the ApiResponse format with appropriate HTTP status codes.

#### Scenario: Validation error
- **WHEN** a request body fails Bean Validation
- **THEN** the system returns HTTP 400 with error_code `VALIDATION_ERROR` and message containing field-level error details

#### Scenario: Resource not found
- **WHEN** a requested resource does not exist
- **THEN** the system returns HTTP 404 with error_code `NOT_FOUND`

#### Scenario: Unexpected server error
- **WHEN** an unhandled RuntimeException occurs
- **THEN** the system returns HTTP 500 with error_code `INTERNAL_ERROR` and a generic message (no stack trace in response)

### Requirement: Request ID generation
Each API request SHALL be assigned a unique request_id (UUID v4) at the filter level. The request_id SHALL be included in the response body and the `X-Request-Id` response header.

#### Scenario: Request ID in response
- **WHEN** any API request is processed
- **THEN** the response includes `request_id` in the body and `X-Request-Id` in the response headers

### Requirement: Portal and Admin route prefixes
The system SHALL support `@RequestMappingPortalV1` mapping to `/api/portal/v1/*` and `@RequestMappingAdminV1` mapping to `/api/admin/v1/*`, registered via ContextPathConfiguration. Existing V1/V2/OpenV1 prefixes SHALL remain unchanged.

#### Scenario: Portal route prefix
- **WHEN** a handler class is annotated with `@RequestMappingPortalV1`
- **THEN** all its endpoints are prefixed with `/api/portal/v1`

#### Scenario: Admin route prefix
- **WHEN** a handler class is annotated with `@RequestMappingAdminV1`
- **THEN** all its endpoints are prefixed with `/api/admin/v1`

#### Scenario: Existing routes unchanged
- **WHEN** the new annotations are added
- **THEN** existing `@RequestMappingApiV1`, `@RequestMappingApiV2`, and `@RequestMappingOpenV1` routes continue to work as before