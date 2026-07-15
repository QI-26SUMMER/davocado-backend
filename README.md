# D-avocado — Server

Backend for D-avocado, a mobile app that photographs an avocado, classifies its ripening
stage (1–5), and tells you how many days remain until your desired ripeness (D-day).

This Spring service owns **authentication, CRUD, D-day calculation, notification scheduling and
the database**. Image inference (ResNet-18) runs in a **separate Python sidecar** that Spring
calls over HTTP (integration lands in a later step).

> Status: scaffolding + shared infrastructure only. Domain entities and APIs are not implemented yet.

## Tech stack

- Java 21 (LTS)
- Spring Boot 3.4.x (Web, Data JPA, Validation, Security)
- Gradle (Groovy DSL)
- PostgreSQL (H2 in tests)
- Auth: self-signup (login id / password) + JWT (single access token) via jjwt
- Docs: springdoc-openapi (Swagger UI)

## Required environment variables

Copy [`.env.example`](.env.example) and fill in real values. Secrets are injected from the
environment — nothing sensitive is hard-coded.

| Variable | Required | Description |
| --- | --- | --- |
| `DB_URL` | yes | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/davocado` |
| `DB_USERNAME` | yes | Database user |
| `DB_PASSWORD` | yes | Database password |
| `JWT_SECRET` | yes | Long random secret for signing JWTs (≥ 32 bytes) |
| `JWT_ACCESS_TTL` | no | Access token TTL in seconds (default `1209600` = 14 days) |
| `JPA_DDL_AUTO` | no | Hibernate DDL mode — `update` locally, `validate` in prod |
| `SPRING_PROFILES_ACTIVE` | no | Active profile (default `local`) |
| `SERVER_PORT` | no | HTTP port (default `8080`) |

All timestamps are handled in **UTC**.

## Running locally

Prerequisites: JDK 21 and a running PostgreSQL with a `davocado` database.

```bash
# 1. Provide config (choose one)
cp .env.example .env          # then edit values, and export them into your shell
# or export the variables directly:
export DB_URL=jdbc:postgresql://localhost:5432/davocado
export DB_USERNAME=davocado
export DB_PASSWORD=changeme
export JWT_SECRET=$(openssl rand -base64 48)

# 2. Build
./gradlew build

# 3. Run (local profile is active by default)
./gradlew bootRun
```

Verify it's up:

```bash
curl http://localhost:8080/health   # -> {"status":"ok"}
```

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI spec: <http://localhost:8080/v3/api-docs>

## Package structure

Domain-oriented layout under `com.davocado.server`:

```
com.davocado.server
├── domain
│   ├── user          # membership, authentication
│   ├── avocado       # avocado items
│   ├── prediction    # predictions + images
│   └── notification  # notifications
└── global
    ├── common        # shared response envelope (ApiResponse), health check
    ├── exception     # ErrorCode, BusinessException, GlobalExceptionHandler
    ├── config        # Security, Swagger, JPA auditing
    └── auth          # JWT utilities + auth filter (skeleton)
```

Each `domain/*` package follows the convention:
`controller / service / repository / entity / dto`.

## API response conventions

**Success** — controllers may return data directly, or wrap it:

```java
return ApiResponse.success(data);   // -> { "data": ... }
```

**Error** — every failure is rendered uniformly:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "사람이 읽을 설명"
  }
}
```

Error codes live in `global/exception/ErrorCode`; throw `BusinessException(ErrorCode)` from
services and `GlobalExceptionHandler` maps it to the right HTTP status.

## Roadmap (next steps)

1. Entities + migrations (DB spec: 6–8 tables)
2. Auth implementation (signup / login, JWT filter)
3. Avocado CRUD
4. Prediction integration (Python sidecar call + D-day calc)
5. Notification scheduling (durable, poll-based)
