# Repository Guidelines

## Project Structure & Module Organization
`src/main/java/com/fan/lazyday` follows a layered layout: `interfaces` contains WebFlux APIs, handlers, and request/response DTOs; `application` holds facades and services; `domain` contains aggregates, entities, repositories, and PO objects; `infrastructure` covers config, security, filters, database helpers, schedulers, and shared utilities. Runtime configuration lives in `src/main/resources/application.yaml`. Flyway SQL migrations are stored in `src/main/resources/db/migration`. Tests mirror production packages under `src/test/java`. Treat `target/` as generated output.

## Build, Test, and Development Commands
- `./mvnw spring-boot:run` starts the backend and applies Flyway migrations using the configured database.
- `./mvnw test` runs the JUnit 5 suite.
- `./mvnw -Dtest=PortalAuthHandlerTest test` runs one test class while iterating.
- `./mvnw clean package` compiles, tests, and builds the jar under `target/`.

The current local defaults in `application.yaml` point to PostgreSQL on `127.0.0.1:5432/lazyday`.

## Coding Style & Naming Conventions
Use Java 21 and 4-space indentation. Follow the existing package boundaries instead of mixing controller, domain, and persistence concerns. Keep reactive entry points returning `Mono` or `Flux`. Use `PascalCase` for classes, `camelCase` for methods and fields, and `V<number>__description.sql` for Flyway files. Place inbound DTOs in `interfaces/request` and outbound DTOs in `interfaces/response`. Reuse Lombok and MapStruct where the project already uses them.

## Testing Guidelines
Tests use JUnit 5, Mockito, Spring Boot test support, and Reactor `StepVerifier`. Name test files `*Test.java` and mirror the package of the class under test. Prefer focused unit tests for handlers, security filters, facades, entities, and utility classes. Add integration coverage when changing database configuration, migrations, or request filters.

## Commit & Pull Request Guidelines
Recent commits follow a Conventional Commit style such as `feat: implement Phase 1 multi-tenant foundation`. Keep the format `type: imperative summary` (`fix: validate refresh token expiry`). PRs should describe behavior changes, database or API impacts, and verification steps. Include sample requests or responses when endpoint, auth-cookie, or quota behavior changes.

## Security & Configuration Tips
Do not commit real secrets or environment-specific credentials. Keep local overrides out of version control and prefer environment variables for API keys. Once a Flyway migration is merged, do not rewrite it; add a new versioned migration for follow-up schema changes.
