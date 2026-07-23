# D-avocado — Server

Backend for D-avocado, a mobile app that photographs an avocado, classifies its ripening
stage (1–5), and tells you how many days remain until your desired ripeness (D-day).

This Spring service owns **authentication, user settings, scan history, image storage and the
database**. Image classification (ResNet-18) runs in a **separate AI service** on Cloud Run that
Spring calls over HTTP. Spring persists exactly what the AI returns — it never computes the
ripeness stage or the D-day itself.

## Architecture

```
iOS app ──HTTPS──> Spring (this repo, Cloud Run)
                      ├── Cloud SQL (PostgreSQL)     — users, scans, images, notifications
                      ├── Cloud Storage (private)    — original/cropped images, served as signed URLs
                      └── AI service (private Cloud Run, separate repo) — stage classification
```

- One photo = one **scan** row (append-only). The target ripeness is a global user setting,
  snapshotted onto each scan so past history stays stable when the setting changes.
- Calls to the private AI service are authenticated with a Google-signed ID token.

## Tech stack

- Java 21 (LTS)
- Spring Boot 3.4.x (Web, Data JPA, Validation, Security)
- Gradle (Groovy DSL)
- PostgreSQL (H2 in tests)
- Auth: self-signup (email / password) + JWT (single access token, no refresh) via jjwt
- Google Cloud Storage (`google-cloud-storage`) for images
- Docs: springdoc-openapi (Swagger UI)

## Required environment variables

Copy [`.env.example`](.env.example) and fill in real values. Secrets are injected from the
environment — nothing sensitive is hard-coded, and `.env` is git-ignored.

| Variable | Required | Description |
| --- | --- | --- |
| `DB_URL` | yes | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/davocado` |
| `DB_USERNAME` | yes | Database user |
| `DB_PASSWORD` | yes | Database password |
| `JWT_SECRET` | yes | Long random secret for signing JWTs (≥ 32 bytes) |
| `JWT_ACCESS_TTL` | no | Access token TTL in seconds (default `1209600` = 14 days) |
| `JPA_DDL_AUTO` | no | Hibernate DDL mode — `update` locally, `validate` in prod |
| `GOOGLE_APPLICATION_CREDENTIALS` | no | Path to a GCP service-account key (for GCS + the AI ID token). Uses Application Default Credentials |
| `GCS_BUCKET` | no | Private bucket for images. Blank disables GCS — image URLs come back `null` |
| `GCS_SIGNED_URL_TTL_MINUTES` | no | Lifetime of a signed image URL (default `15`) |
| `AI_BASE_URL` | no | Inference service URL. Blank disables scan creation (`POST /scans` returns 502) |
| `AI_PREDICT_PATH` | no | Predict path on the AI service (default `/predict`) |
| `AI_CONNECT_TIMEOUT_SECONDS` | no | AI connect timeout (default `10`) |
| `AI_READ_TIMEOUT_SECONDS` | no | AI read timeout (default `60`, generous for Cloud Run cold starts) |
| `AI_USE_ID_TOKEN` | no | Send a Google ID token to the (private) AI service (default `true`) |
| `SPRING_PROFILES_ACTIVE` | no | Active profile (default `local`) |
| `SERVER_PORT` | no | HTTP port (default `8080`) |

The app boots without GCS or the AI service configured — those features are simply disabled
(images return `null`, scan creation returns 502) so the rest of the API stays usable. All
timestamps are handled in **UTC**.

## Running locally

Prerequisites: JDK 21 and a reachable PostgreSQL with a `davocado` database.

```bash
# 1. Provide config
cp .env.example .env          # then edit values and export them into your shell

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

> On Windows with a Korean-character user path, `./gradlew test` fails from the CLI due to a
> Gradle worker-argfile encoding issue (not a code problem). Build under an ASCII path via
> `subst` — see `CLAUDE.md` for the exact workaround.

## API overview

All endpoints are under the service root; browse the full contract in Swagger.

| Area | Endpoints |
| --- | --- |
| Auth (`/auth`) | `signup`, `login`, `logout`, `password/reset` |
| Users & settings (`/users`) | `GET/PATCH /me`, `PATCH /me/settings`, `PUT /me/push-token` |
| Scans (`/scans`) | `POST /scans` (multipart upload → classify → store), `GET /scans`, `GET /scans/stats`, `GET /scans/{id}`, `DELETE /scans/{id}` |

Email is the login identifier; every endpoint except signup/login/password-reset and the docs
requires `Authorization: Bearer {token}`.

## Package structure

Domain-oriented layout under `com.davocado.server`:

```
com.davocado.server
├── domain
│   ├── user          # membership, authentication, settings
│   ├── scan          # scans + images (the core), AI client
│   └── notification  # notifications
└── global
    ├── common        # shared response envelope (ApiResponse), health check
    ├── exception     # ErrorCode, BusinessException, GlobalExceptionHandler
    ├── config        # Security, Swagger, JPA auditing, web
    ├── auth          # JWT utilities + auth filter
    └── storage       # GCS upload + signed URLs
```

Each `domain/*` package follows the convention:
`controller / service / repository / entity / dto`.

## API response conventions

**Success** — wrapped in a `data` envelope:

```java
return ApiResponse.success(data);   // -> { "data": ... }
```

**Error** — every failure is rendered uniformly:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable description"
  }
}
```

JSON is serialized in `snake_case`. Error codes live in `global/exception/ErrorCode`; throw
`BusinessException(ErrorCode)` from services and `GlobalExceptionHandler` maps it to the right
HTTP status.

## Deployment

Containerized and deployed to **Cloud Run**, backed by **Cloud SQL** (PostgreSQL). The AI
inference service is a separate repository, also on Cloud Run, called privately with an ID token.
