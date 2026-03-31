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

**Scope note**: The user explicitly requested full CRUD (create, read, update, delete).
The original spec marked room deletion and renaming as out of scope for v1. This plan
expands scope to include them per the explicit planning instruction.

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
│           │   │   ├── config/
│           │   │   │   ├── SecurityConfig.java      (JWT filter chain; no session)
│           │   │   │   └── JwtAuthFilter.java       (OncePerRequestFilter: validate JWT)
│           │   │   ├── controller/
│           │   │   │   ├── RoomController.java
│           │   │   │   └── dto/
│           │   │   │       ├── RoomResponse.java
│           │   │   │       └── UpdateRoomRequest.java
│           │   │   ├── service/
│           │   │   │   ├── RoomService.java          (business logic)
│           │   │   │   └── JwtService.java           (JWT validation only)
│           │   │   └── repository/
│           │   │       └── RoomRepository.java       (jOOQ DSL)
│           │   └── resources/
│           │       ├── application.yml
│           │       └── db/migration/
│           │           ├── V1__create_rooms.sql
│           │           ├── V2__create_user_room_stats.sql
│           │           └── V3__create_room_audit_log.sql
│           └── test/
│               └── java/com/privchat/rooms/
│                   ├── filter/JwtAuthFilterTest.java
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


## Summary

Build `rooms-service` — a new Spring Boot 4.0.4 / Java 25 microservice providing
full CRUD for public chat rooms. Session authentication is resolved via the shared
Spring Session JDBC store (same PostgreSQL instance used by `entry-auth-service`);
no direct HTTP inter-service calls are required for auth. The API gateway gains a
`RoomsProxyController` that forwards all `/rooms/**` traffic to `rooms-service` (same
proxy pattern as `AuthProxyController`). The existing Next.js frontend is updated to
call the gateway's `/rooms` endpoints.

**Scope note**: The user explicitly requested full CRUD (create, read, update, delete).
The original spec marked room deletion and renaming as out of scope for v1. This plan
expands scope to include them per the explicit planning instruction. The spec has been
updated accordingly via this plan.

## Technical Context

**Language/Version**: Java 25 (LTS)
**Framework**: Spring Boot 4.0.4 (same as `entry-auth-service`)
**Build**: Gradle (Groovy DSL, same as `entry-auth-service`)
**Primary Dependencies**: Spring Web, Spring Security, Spring Session JDBC,
jOOQ 3.20.4, Flyway 11.20.3, Bucket4j 8.10.1 (room creation cap enforcement),
Testcontainers 1.21.4 (PostgreSQL for integration tests)
**Storage**: PostgreSQL 17 — shared instance; `rooms-service` owns `rooms`,
`user_room_stats`, and `room_audit_log` tables
**Testing**: JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL)
**Target Platform**: Docker container on `privchat-net` (existing Docker network)
**Project Type**: REST microservice
**Performance Goals**: < 200ms p95 for all room list/CRUD operations
**Constraints**: All `/rooms/**` endpoints require a valid session cookie; the
room creation hard cap of 10 per user is enforced server-side
**Scale/Scope**: Small private portal; bounded by 10 active rooms per user

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

### I. Privacy by Design ✅ (with documented exception)

Room metadata (names, creator username, timestamps, occupant count) is explicitly
designated as **plaintext** per spec clarification Q2. This is a documented exception
to the zero-knowledge server principle — justified because:
- Rooms are already public and only accessible behind the network password gate
- Encrypting list metadata would block real-time sync without meaningful privacy gain
- Room *content* (messages) remains out of scope for this feature entirely

**Required action**: Threat model below formally records this decision.

### II. Security First ✅

- Spring Security enforces authenticated session on all `/rooms/**` endpoints
- Session verified via shared Spring Session JDBC (no credentials re-transmitted)
- All jOOQ queries are parameterized (SQL injection prevented by design)
- Room creation cap (max 10 per user) enforced server-side, not client-side
- Creator-only enforcement on update/delete (username from session, never from request body)
- `room_audit_log` captures all mutation events (CREATE, UPDATE, DELETE)
- `rooms-service` not directly exposed; only accessible via gateway on internal network

### III. Test-First ✅

TDD is mandatory. All tests written before implementation code. Coverage gates:
- Unit tests for `RoomService` (creation cap logic, naming sequence, creator check)
- Integration tests (Testcontainers PostgreSQL) for all CRUD endpoints
- Security tests: unauthenticated requests return 401; non-creator update/delete return 403

### IV. Web-First ✅

REST API consumed by the existing Next.js frontend. No framework-specific coupling.
All responses are plain JSON.

### V. Simplicity & YAGNI ✅

Shared Spring Session JDBC eliminates inter-service HTTP calls for auth — the simplest
approach that satisfies the security requirement. The `RoomsProxyController` in the
gateway reuses the identical proxy pattern from `AuthProxyController`.

## Threat Model

| Threat | Mitigation |
|--------|------------|
| Unauthenticated room access | Spring Security `anyRequest().authenticated()` on all `/rooms/**` routes |
| Session hijacking | Existing entry-auth-service cookie security (HttpOnly, Secure, SameSite=Strict) |
| Room metadata plaintext on server | Accepted risk (documented exception). Mitigated: only authenticated portal members can read; portal gated by shared network password |
| SQL injection | jOOQ parameterized queries — no raw SQL string concatenation permitted |
| Room creation spam | Hard cap: max 10 active rooms per user, enforced in `RoomService` via `user_room_stats.active_rooms_count` |
| Unauthorized room mutation (update/delete by non-creator) | Creator username read from session attributes, compared to `rooms.creator_username`; returns 403 if mismatch |
| Gateway bypass (direct rooms-service access) | `rooms-service` has no published Docker port; only accessible on `privchat-net` internal network |
| Audit log tampering | `room_audit_log` is append-only from application code; no UPDATE/DELETE paths |

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
│   ├── entry-auth-service/         ← existing, unchanged
│   └── rooms-service/              ← NEW
│       ├── build.gradle
│       ├── Dockerfile
│       └── src/
│           ├── main/
│           │   ├── java/com/privchat/rooms/
│           │   │   ├── RoomsServiceApplication.java
│           │   │   ├── config/
│           │   │   │   ├── SecurityConfig.java    (Spring Security: all routes authenticated)
│           │   │   │   └── SessionConfig.java     (Spring Session JDBC, shared store)
│           │   │   ├── controller/
│           │   │   │   ├── RoomController.java    (CRUD endpoints)
│           │   │   │   └── dto/
│           │   │   │       ├── CreateRoomResponse.java
│           │   │   │       ├── RoomResponse.java
│           │   │   │       └── UpdateRoomRequest.java
│           │   │   ├── service/
│           │   │   │   └── RoomService.java       (business logic, cap enforcement)
│           │   │   ├── repository/
│           │   │   │   └── RoomRepository.java    (jOOQ DSL)
│           │   │   └── model/
│           │   │       └── Room.java              (plain Java record)
│           │   └── resources/
│           │       ├── application.yml
│           │       └── db/migration/
│           │           ├── V1__create_rooms.sql
│           │           ├── V2__create_user_room_stats.sql
│           │           └── V3__create_room_audit_log.sql
│           └── test/
│               └── java/com/privchat/rooms/
│                   ├── service/
│                   │   └── RoomServiceTest.java
│                   ├── controller/
│                   │   └── RoomControllerTest.java
│                   └── integration/
│                       └── RoomCrudIntegrationTest.java
│
├── api-gateway/                    ← updated
│   └── src/main/java/com/privchat/gateway/
│       ├── proxy/
│       │   ├── AuthProxyController.java   (existing, unchanged)
│       │   └── RoomsProxyController.java  (NEW — mirrors AuthProxyController pattern)
│       └── resources/
│           └── application.yml            (add ROOMS_SERVICE_URL)
│
├── frontend/                       ← updated
│   └── src/
│       ├── app/
│       │   └── rooms/             (new page — Room Gateway screen)
│       └── lib/
│           └── api/rooms.ts       (API client calls to /rooms)
│
└── docker-compose.yml              ← updated (add rooms-service service)
```

**Structure Decision**: Multi-service layout extending the existing pattern.
`rooms-service` mirrors `entry-auth-service` structure exactly. Gateway extended
with one additional proxy controller. Frontend gets one new page and one API client.

## Complexity Tracking

No constitution violations. No unjustified complexity introduced.

| Addition | Why Needed | YAGNI Justification |
|----------|------------|---------------------|
| `user_room_stats` table | Track monotonic creation counter (naming) + active count (cap) separately | Single table is simpler than two; both counters needed at creation time |
| `room_audit_log` table | Constitution Security Requirement: audit trail for all mutations | Required by constitution; reuses identical pattern from `security_audit_log` |

