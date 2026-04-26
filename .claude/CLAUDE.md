# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Lazyday is a **Spring Boot 4.0.6 reactive backend** (Java 21) that implements AI-powered services using domain-driven design patterns. It supports multiple databases (PostgreSQL, MySQL) and integrates with OpenAI/Aliyun DashScope for AI capabilities including embeddings via pgvector.

The architecture follows a strict 4-layer pattern:
- **Interfaces** (REST API, handlers, DTOs)
- **Application** (facades, services, mappers)
- **Domain** (entities, aggregates, repositories with pure business logic)
- **Infrastructure** (database config, utilities, context management, web filters)

## Build & Run Commands

### Build and Package
```bash
cd backend
./mvnw clean package
```

### Run Tests
```bash
# All tests
./mvnw test

# Single test class
./mvnw test -Dtest=LazydayApplicationTests

# With coverage
./mvnw clean test jacoco:report
```

### Run Application
```bash
cd backend
./mvnw spring-boot:run
```
Server starts on `http://localhost:8080` by default.

### Build Docker Image
```bash
cd backend
./mvnw spring-boot:build-image
```

### Compile Only (without tests)
```bash
./mvnw clean compile
```

### Check for Issues
```bash
# Dependency vulnerabilities
./mvnw dependency-check:check

# Compiler warnings
./mvnw clean compile
```

## Architecture & Key Design Patterns

### Layered Architecture Flow

**Request Flow**: `DemoHandler` (REST controller) → `DemoFacade` (orchestration) → `DemoService` (business logic) → `UserRepository` (data access)

### Core Layers

1. **Interfaces** (`interfaces/`)
   - Controllers implement interface definitions (see DemoHandler implements DemoApi)
   - Use annotation-based routing: `@RequestMappingApiV1`, `@RequestMappingApiV2`, `@RequestMappingOpenV1`
   - All endpoints return reactive types: `Mono<T>` or `Flux<T>`
   - ContextPathConfiguration automatically adds prefix based on annotation

2. **Application** (`application/`)
   - Facades orchestrate cross-service operations
   - Services contain business logic, use MapStruct for BO transformation
   - MapStruct processors generate mapping code at compile time (see `maven-compiler-plugin` with annotation processing)

3. **Domain** (`domain/`)
   - Pure domain logic with DDD aggregates (e.g., UserEntity, UserAggregation)
   - POs (persistence objects) extend BaseAllUserTime for audit fields (created_by, created_time, updated_by, updated_time)
   - Repositories use R2dbcEntityTemplate directly (not Spring Data Repositories) for full control
   - R2dbcHelper provides field name mapping utilities

4. **Infrastructure** (`infrastructure/`)
   - **DB Config**: Flyway handles migrations (`db/migration/` and dialect-specific folders). Custom codecs in R2dbcConfiguration handle UUID and JSON serialization.
   - **Web Config**: ContextPathConfiguration maps endpoints to paths based on controller annotations
   - **Filters**: AppWebFilter logs requests and measures performance (logs if > 200ms)
   - **Properties**: ServiceProperties (fan.service.*) and DatabaseProperties (database.*) loaded from application.yaml
   - **Context**: SpringContext provides lazy bean access pattern (see UserEntity.CTX usage)

### Reactive & R2DBC Patterns

- **All database operations are non-blocking** via R2DBC with connection pooling (10 initial, 100 max)
- **Custom Converters**: JsonConverter and UUIDConverter in CustomR2dbcCustomConversions handle special types
- **Transaction Management**: ReactiveTransactionManager and TransactionalOperator for reactive transactions
- **R2dbcEntityTemplate**: Direct template usage in repositories for custom queries (see UserRepository.getAll)

### Dependency Injection Pattern

UserEntity demonstrates the "lazy bean" pattern used throughout:
```java
public final static Lazy<Context> CTX = SpringContext.getLazyBean(Context.class);
```
This defers Spring context access until needed and provides type-safe access to dependencies.

## Configuration

### Database Setup (application.yaml)
```yaml
database:
  dialect: postgresql      # or mysql
  type: postgresql
  host: 127.0.0.1
  port: 5432
  dbname: lazyday
  username: postgres
  password: postgres
```

### API Endpoints
- **v1 API**: `/api/lazyday/v1/*` (RequestMappingApiV1 controllers)
- **v2 API**: `/api/lazyday/v2/*` (RequestMappingApiV2 controllers)
- **Public API**: `/api/open/v1/*` (RequestMappingOpenV1 controllers)

### AI & Embeddings (application.yaml)
```yaml
spring:
  ai:
    openai:
      api-key: sk-xxx           # Aliyun DashScope
      base-url: https://dashscope.aliyuncs.com/compatible-mode/
      chat:
        options:
          model: qwen3.6-max-preview
      embedding:
        options:
          model: text-embedding-v4
          dimensions: 1536       # Must match pgvector table schema
```

## Important Implementation Notes

### When Adding New Features

1. **Database Migrations**: Add SQL scripts to `backend/src/main/resources/db/migration/` with naming pattern `V{N}__{description}.sql`. Dialect-specific scripts in `db/migration-postgresql/` or `db/migration-mysql/`.

2. **New API Endpoints**: 
   - Create interface in `interfaces/api/`
   - Implement in `interfaces/handler/` with appropriate `@RequestMappingApiV*` annotation
   - Return `Mono<T>` for single values or `Flux<T>` for collections
   - Handler injects Facade and Service via constructor

3. **Business Logic**:
   - Implement Service interface in `application/service/impl/`
   - Use MapStruct mapper for BO transformation (processor generates code at compile)
   - Access repositories through DI

4. **Domain Models**:
   - Extend BaseAllUserTime or BaseCreateUserTime for audit timestamp tracking
   - Mark PO class with `@Table("table_name")` and `@Id` annotations
   - Implement repository as `@Component` using R2dbcEntityTemplate

5. **MapStruct Mappers**: Must be annotated with `@Mapper` and configured in maven-compiler-plugin's annotationProcessorPaths (already configured in pom.xml).

### Custom Type Handling

UUID and JSON types require custom converters. If adding new custom types:
- Implement R2dbc Converter
- Register in R2dbcConfiguration.CustomR2dbcCustomConversions.DEFAULT_CONVERTERS
- Update CustomConverterConfiguration if using template methods

### Testing

- Use `@SpringBootTest` for integration tests
- Database tests automatically use Flyway migrations to init schema
- Current test count: minimal (only LazydayApplicationTests.contextLoads())

## Project Structure

```
backend/
├── src/main/java/com/fan/lazyday/
│   ├── interfaces/        # HTTP layer
│   ├── application/       # Business logic & facades
│   ├── domain/           # DDD aggregates & repositories
│   └── infrastructure/   # Config, utilities, filters
├── src/main/resources/
│   ├── application.yaml  # Configuration
│   └── db/
│       ├── migration/           # Common migrations
│       ├── migration-postgresql/
│       └── migration-mysql/
├── src/test/java/        # Minimal tests
├── pom.xml              # Maven config
└── mvnw / mvnw.cmd      # Maven wrapper
```

## Dependencies & Versions

- **Spring Boot**: 4.0.6
- **Spring AI**: 2.0.0-M4 (OpenAI, pgvector)
- **R2DBC**: 1.1.1 (PostgreSQL), 1.4.1 (MySQL)
- **MapStruct**: 1.5.5 (compile-time code generation)
- **Flyway**: Latest (included via Spring Boot)
- **Lombok**: 1.18.30 (annotation processing)
- **Java**: 21

All annotation processors configured in maven-compiler-plugin for compile-time processing (Lombok, MapStruct, Spring Boot configuration processor).

## Common Gotchas

1. **R2DBC Dialect Differences**: PostgreSQL uses different codecs than MySQL. Check `infrastructure/config/db/{postgresql,mysql}/` for database-specific configurations.

2. **Lazy Bean Access**: Always access injected beans through the Lazy pattern (e.g., `UserEntity.CTX.get().getRepository()`) rather than direct Spring DI in entities.

3. **Connection Pooling**: R2DBC pool is configured (10 initial, 100 max). Adjust `spring.r2dbc.pool.*` in application.yaml if needed.

4. **Flyway Migrations**: Out-of-order migrations are allowed (`flyway.out-of-order: true`), but avoid relying on this for production. Baseline is created on first migration.

5. **Reactive Types Required**: All service methods must return `Mono` or `Flux`. Blocking operations block the entire thread pool.

6. **AI API Key**: Stored in plain text in application.yaml. Use environment variables or secrets management for production (e.g., `SPRING_AI_OPENAI_API_KEY`).
