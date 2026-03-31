# Implementation Plan: Room Gateway

**Branch**: `002-room-gateway` | **Date**: 2026-03-31 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/002-room-gateway/spec.md`

## Summary

Build `rooms-service` — a new Spring Boot 4.0.4 / Java 25 microservice providing
full CRUD for public chat rooms. `rooms-service` owns its own dedicated PostgreSQL
database (separate Docker container — true microservices database isolation). Session
authentication between the browser and `entry-auth-service` is unchanged (server-side
sessions). Inter-service authentication uses **JWT (RS256)**: `entry-auth-service`
issues a short-lived JWT on login and exposes both a refresh endpoint and a JWKS
endpoint (`GET /auth/jwks`); `rooms-service` fetches the RSA public key once at
startup and validates JWTs locally — no per-request HTTP calls to
`entry-auth-service`. The API gateway gains a `RoomsProxyController` that forwards
`/rooms/**` traffic including the JWT `Authorization` header to `rooms-service`.

**Scope**: Full CRUD — create, read, update (rename), delete. Rename and delete are
creator-only actions (US4, P4). Spec updated with FR-013, FR-014, FR-015 and US4.

## Technical Context

**Language/Version**: Java 25 (LTS)
**Framework**: Spring Boot 4.0.4 (same as `entry-auth-service`)
**Build**: Gradle (Groovy DSL, same as `entry-auth-service`)
**Primary Dependencies**:
- Spring Web, Spring Security (rooms-service)
- JJWT 0.12.6 (JWT issue + validation — both services; RS256 key pair)
- jOOQ 3.20.4, Flyway 11.20.3 (rooms-service)
- Testcontainers 1.21.4 (PostgreSQL for integration tests)
**Storage**:
- `postgres-rooms` — dedicated PostgreSQL 17 container for rooms-service
- `postgres` — existing container for entry-auth-service (unchanged)
**Testing**: JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL)
**Target Platform**: Docker containers on `privchat-net` (existing Docker network)
**Project Type**: REST microservice
**Performance Goals**: < 200ms p95 for all room list/CRUD operations; JWT validation
is local (no network hop) — negligible auth overhead
**Constraints**: All `/rooms/**` endpoints require `Authorization: Bearer <jwt>`;
room creation hard cap of 10 per user enforced server-side
**Scale/Scope**: Small private portal; bounded by 10 active rooms per user

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

### I. Privacy by Design ✅ (with documented exception)

Room metadata (names, creator username, timestamps, occupant count) is explicitly
designated as **plaintext** per spec clarification Q2. Documented exception to the
zero-knowledge server principle — justified because rooms are public and only
accessible behind the network password gate. Room *content* (messages) is entirely
out of scope for this feature. Threat model below records this formally.

### II. Security First ✅

- Spring Security JWT filter enforces authentication on all `/rooms/**` endpoints
- JWT signed RS256 with RSA key pair; private key never leaves `entry-auth-service`
- Public key distributed via `GET /auth/jwks` (JWKS format); rooms-service caches on startup
- Short-lived JWT (15 minutes) limits the revocation window on logout
- Creator-only enforcement on update/delete: username from JWT claims, never from request body
- Room creation cap (max 10 per user) enforced server-side in `RoomService`
- All mutation operations logged to `room_audit_log` (append-only)
- `rooms-service` has no published Docker port; accessible only via `privchat-net`
- Separate PostgreSQL instance for rooms: no shared credentials with auth DB

### III. Test-First ✅

TDD mandatory. All tests written before implementation. Coverage gates:
- Unit tests: `RoomService` (cap logic, naming sequence, creator check), `JwtValidationFilter`
- Integration tests (Testcontainers): all 5 CRUD endpoints, JWT rejection (missing/expired/invalid)
- Security tests: 401 on missing JWT, 403 on non-creator mutation

### IV. Web-First ✅

REST API consumed by the existing Next.js frontend. JWT stored in memory (not
localStorage) by the frontend — avoids XSS exposure. Authorization header sent
on all rooms API calls.

### V. Simplicity & YAGNI ✅

JWKS-based JWT validation: `entry-auth-service` signs with RSA private key and exposes
`GET /auth/jwks`; `rooms-service` fetches public key once at startup and validates
locally — no per-request inter-service HTTP calls. Private key never leaves the issuing
service — compromised `rooms-service` cannot forge tokens.
service to docker-compose but is required for true DB isolation.

## Threat Model

| Threat | Mitigation |
|--------|------------|
| Unauthenticated room access | Spring Security `anyRequest().authenticated()` + JWT filter on all `/rooms/**` |
| JWT revocation window on logout | Short expiry (15 min); frontend discards JWT on logout; session cookie deleted by entry-auth-service |
| JWT private key exposure | Private key injected via `JWT_PRIVATE_KEY` env var (base64 PEM); never in source code or logs; only in `entry-auth-service` |
| JWT tampering / forging | RS256 signature verified by JJWT on every request; invalid signature → 401 |
| Room metadata plaintext on server | Accepted risk (documented exception). Mitigated: only authenticated users (valid JWT) can read |
| SQL injection in rooms-service | jOOQ parameterized queries — no raw SQL string concatenation permitted |
| Room creation spam (10-room cap) | `user_room_stats.active_rooms_count` checked + enforced atomically in transaction |
| Unauthorized room mutation (non-creator) | `rooms.creator_username` compared to JWT `sub` claim; returns 403 if mismatch |
| Cross-DB credential leak | Separate PostgreSQL containers with separate credentials; rooms-service has no credentials to auth DB |
| Gateway bypass | `rooms-service` has no published Docker port; only accessible on `privchat-net` |
| Audit log tampering | `room_audit_log` is append-only from application code |

## Project Structure

### Documentation (this feature)

```text
specs/002-room-gateway/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
├── contracts/
│   └── rooms-service-contract.md   ← Phase 1 output
└── tasks.md             ← Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code

```text
implementation/
├── services/
│   ├── entry-auth-service/              ← updated (JWT issuance added)
│   │   └── src/main/java/com/privchat/auth/
│   │       ├── service/
│   │       │   ├── AuthService.java        (updated: issue JWT on join)
│   │       │   └── JwtService.java         (NEW: JWT generation + signing)
│   │       ├── controller/
│   │       │   ├── AuthController.java     (updated: return token in join response)
│   │       │   └── dto/
│   │       │       ├── JoinResponse.java   (updated: add token field)
│   │       │       └── TokenResponse.java  (NEW: for refresh endpoint)
│   │       └── resources/application.yml  (add JWT_PRIVATE_KEY, JWT_PUBLIC_KEY, JWT_EXPIRY_SECONDS)
│   │
│   └── rooms-service/                   ← NEW
│       ├── build.gradle
│       ├── Dockerfile
│       └── src/
│           ├── main/
│           │   ├── java/com/privchat/rooms/
│           │   │   ├── RoomsServiceApplication.java
│           │   │   ├── security/
│           │   │   │   ├── SecurityConfig.java      (JWT filter chain; no session)
│           │   │   │   ├── JwtAuthFilter.java       (OncePerRequestFilter: validate JWT)
│           │   │   │   ├── JwksClient.java          (fetch + cache RSAPublicKey)
│           │   │   │   └── JwtService.java          (JWT validation only)
│           │   │   ├── model/
│           │   │   │   ├── Room.java
│           │   │   │   ├── UserRoomStats.java
│           │   │   │   └── RoomAuditLog.java
│           │   │   ├── controller/
│           │   │   │   ├── RoomController.java
│           │   │   │   └── dto/
│           │   │   │       ├── RoomResponse.java
│           │   │   │       ├── CreateRoomRequest.java
│           │   │   │       └── UpdateRoomRequest.java
│           │   │   ├── service/
│           │   │   │   └── RoomService.java         (business logic)
│           │   │   └── repository/
│           │   │       ├── RoomRepository.java      (jOOQ DSL)
│           │   │       └── AuditLogRepository.java
│           │   └── resources/
│           │       ├── application.yml
│           │       └── db/migration/
│           │           ├── V1__create_rooms.sql
│           │           ├── V2__create_user_room_stats.sql
│           │           └── V3__create_room_audit_log.sql
│           └── test/
│               └── java/com/privchat/rooms/
│                   ├── security/JwtAuthFilterTest.java
│                   ├── service/RoomServiceTest.java
│                   ├── controller/RoomControllerTest.java
│                   └── integration/RoomCrudIntegrationTest.java
│
├── api-gateway/                         ← updated
│   └── src/main/java/com/privchat/gateway/proxy/
│       └── RoomsProxyController.java    (NEW — mirrors AuthProxyController)
│   └── src/main/resources/application.yml  (add ROOMS_SERVICE_URL)
│
├── frontend/                            ← updated
│   └── src/
│       ├── app/rooms/                   (new page — Room Gateway screen)
│       └── lib/api/rooms.ts             (API client: sends Authorization: Bearer header)
│
└── docker-compose.yml                   ← updated
    # adds: postgres-rooms, rooms-service; updates: api-gateway depends_on + env
```

**Structure Decision**: Multi-service layout extending the existing pattern.
`rooms-service` mirrors `entry-auth-service` structure. One new PostgreSQL container
(`postgres-rooms`) provides true database isolation. Gateway extended with one proxy
controller. Frontend gets one new page + API client.

## Complexity Tracking

| Addition | Why Needed | YAGNI Justification |
|----------|------------|---------------------|
| Separate `postgres-rooms` container | True DB isolation per microservices principle (user requirement) | Cannot share DB if isolation is required |
| JJWT + RS256 key pair | JWT issue/sign (entry-auth) + JWKS endpoint + local validation (rooms) | Private key stays in entry-auth; no shared secret coupling |
| `user_room_stats` table | Track naming counter + active cap atomically | Single table; both counters needed in same transaction |
| `room_audit_log` table | Constitution: audit trail for all mutations | Required; reuses pattern from `security_audit_log` |


---

## Dependency Audit

Per constitution §II (Security First), all newly adopted dependencies were reviewed
against [NIST NVD](https://nvd.nist.gov/) and [GitHub Advisories](https://github.com/advisories)
before adoption.

| Dependency | Version | Audit Result |
|------------|---------|--------------|
| `io.jsonwebtoken:jjwt-api` | 0.12.6 | No known CVEs at time of planning (2026-03-31) |
| `io.jsonwebtoken:jjwt-impl` | 0.12.6 | No known CVEs at time of planning |
| `io.jsonwebtoken:jjwt-jackson` | 0.12.6 | No known CVEs at time of planning |
| `org.jooq:jooq` | 3.20.4 | No known CVEs at time of planning |
| `org.flywaydb:flyway-core` | 11.20.3 | No known CVEs at time of planning |
| `org.testcontainers:postgresql` | 1.21.4 | No known CVEs at time of planning |
| `org.postgresql:postgresql` (JDBC driver) | inherited from Spring Boot BOM | No known CVEs at time of planning |

**Re-audit required** before any version upgrade. Use `./gradlew dependencyCheckAnalyze`
(OWASP Dependency-Check) as part of the CI pipeline.
