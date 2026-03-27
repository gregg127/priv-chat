---
description: "Task list for Network Entry Gate implementation"
---

# Tasks: Network Entry Gate

**Input**: Design documents from `/specs/001-network-entry-gate/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**TDD Notice**: Per the project constitution (Principle III — Test-First, NON-NEGOTIABLE), tests MUST be written and confirmed failing before implementation begins. Test tasks appear before implementation tasks in every user story phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)

---

## Phase 1: Setup

**Purpose**: Repository structure, project scaffolding, and Docker infrastructure

- [X] T001 Create `implementation/` directory structure per plan.md project layout
- [X] T002 [P] Initialise `entry-auth-service` Gradle project (Spring Boot 4.0.4, Java 25) at `implementation/services/entry-auth-service/build.gradle` with dependencies: `spring-boot-starter-web`, `spring-boot-starter-jooq`, `spring-session-jdbc`, `flyway-core`, `postgresql`, `bucket4j-core`, `caffeine`
- [X] T003 [P] Initialise `api-gateway` Gradle project (Spring Cloud Gateway 5.0.x) at `implementation/api-gateway/build.gradle` with dependency: `spring-cloud-starter-gateway-mvc`
- [X] T004 [P] Initialise Next.js 15 + TypeScript frontend at `implementation/frontend/` via `npx create-next-app@latest frontend --typescript --app` and configure `output: 'standalone'` in `implementation/frontend/next.config.ts`
- [X] T005 [P] Create `implementation/docker-compose.yml` with 4 services: `postgres` (postgres:17-alpine), `entry-auth-service`, `api-gateway`, `frontend`; internal `privchat-net` bridge network; only `api-gateway` (8080) and `frontend` (3000) expose host ports
- [X] T006 [P] Create `implementation/docker-compose.override.yml` for local dev: mount source volumes for hot reload, set `SPRING_PROFILES_ACTIVE=dev`
- [X] T007 [P] Create `implementation/.env.example` with `NETWORK_PASSWORD`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `SESSION_TIMEOUT_SECONDS=86400`; add `.env` to `implementation/.gitignore`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Database schema, shared config, and cross-cutting infrastructure that ALL user stories depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T008 Create Flyway migration `V1__create_security_audit_log.sql` at `implementation/services/entry-auth-service/src/main/resources/db/migration/V1__create_security_audit_log.sql` — table: `id BIGSERIAL PK`, `event_type VARCHAR(50) CHECK IN ('JOIN_SUCCESS','JOIN_FAILURE','RATE_LIMITED')`, `ip_address VARCHAR(45) NOT NULL`, `username VARCHAR(64) NULL`, `occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`; indexes on `(ip_address, occurred_at DESC)` and `(event_type, occurred_at DESC)`
- [X] T009 [P] Create `SecurityAuditLog` plain Java record at `implementation/services/entry-auth-service/src/main/java/com/privchat/auth/model/SecurityAuditLog.java` with fields: `eventType`, `ipAddress`, `username` (no JPA annotations)
- [X] T010 [P] Create `AuditLogService` at `implementation/services/entry-auth-service/src/main/java/com/privchat/auth/service/AuditLogService.java`: injects `DSLContext`; `log(SecurityAuditLog)` method inserts into `security_audit_log` table via jOOQ DSL; runs in `REQUIRES_NEW` transaction
- [X] T011 [P] Configure Spring Session JDBC in `implementation/services/entry-auth-service/src/main/java/com/privchat/auth/config/SessionConfig.java`: `@EnableJdbcHttpSession`, `DefaultCookieSerializer` with `HttpOnly=true`, `Secure=true`, `SameSite=Strict`, `CookiePath=/`; session timeout from `SESSION_TIMEOUT_SECONDS` env var
- [X] T012 [P] Configure Spring Security in `implementation/services/entry-auth-service/src/main/java/com/privchat/auth/config/SecurityConfig.java`: permit all on `/auth/**` and `/actuator/health`; disable CSRF (cookie-based session with `SameSite=Strict` is CSRF-safe); no form login
- [X] T013 [P] Configure datasource and Flyway in `implementation/services/entry-auth-service/src/main/resources/application.yml`: datasource pointing to `postgres` container, Flyway `baseline-on-migrate=true`, `spring.session.store-type=jdbc`, `server.port=8080`
- [X] T014 [P] Configure API gateway routing in `implementation/api-gateway/src/main/resources/application.yml`: route `/auth/**` → `http://entry-auth-service:8080`; add `X-Forwarded-For` header filter; `server.port=8080`
- [X] T015 [P] Create `implementation/services/entry-auth-service/Dockerfile` (multi-stage: `eclipse-temurin:25-jdk` builder → `eclipse-temurin:25-jre` runner; Gradle build inside container)
- [X] T016 [P] Create `implementation/api-gateway/Dockerfile` (same multi-stage pattern as T015)
- [X] T017 [P] Create `implementation/frontend/Dockerfile` (multi-stage: `node:22-alpine` builder runs `next build` → `node:22-alpine` runner copies `.next/standalone` and runs `node server.js`)

**Checkpoint**: `docker compose up postgres entry-auth-service api-gateway` starts cleanly; `GET http://localhost:8080/auth/actuator/health` returns `{"status":"UP"}`

---

## Phase 3: User Story 1 — Joining the Network (Priority: P1) 🎯 MVP

**Goal**: New user enters username + shared network password → admitted to portal. Rate limiting, loading state, validation, and security logging all included.

**Independent Test**: Open http://localhost:3000, enter a username + correct password, click "Join network" → land on portal interior. Enter wrong password → see error. 5 wrong passwords → see lockout message.

### Tests for User Story 1 ⚠️ WRITE AND CONFIRM FAILING BEFORE T024

- [X] T018 [P] [US1] Write failing unit test `RateLimitServiceTest` in `implementation/services/entry-auth-service/src/test/java/com/privchat/auth/service/RateLimitServiceTest.java`: test that IP is allowed for first 5 attempts, blocked on 6th, unblocked after 10 min window
- [X] T019 [P] [US1] Write failing unit test `AuthServiceTest` in `implementation/services/entry-auth-service/src/test/java/com/privchat/auth/service/AuthServiceTest.java`: test correct password creates session + logs `JOIN_SUCCESS`; wrong password returns error + logs `JOIN_FAILURE`; rate-limited IP logs `RATE_LIMITED`
- [X] T020 [P] [US1] Write failing unit test `AuthControllerTest` in `implementation/services/entry-auth-service/src/test/java/com/privchat/auth/controller/AuthControllerTest.java`: `POST /auth/join` returns 200 with username on success; 401 on wrong password; 429 with `Retry-After` header when rate-limited; 400 on blank username or password
- [X] T021 [P] [US1] Write failing integration test `JoinIntegrationTest` in `implementation/services/entry-auth-service/src/test/java/com/privchat/auth/integration/JoinIntegrationTest.java`: full `POST /auth/join` flow with `@SpringBootTest` + Testcontainers PostgreSQL; verify `SESSION` cookie is set `HttpOnly`/`Secure`/`SameSite=Strict`; verify `security_audit_log` row inserted

### Implementation for User Story 1

- [X] T022 [US1] Implement `RateLimitService` at `implementation/services/entry-auth-service/src/main/java/com/privchat/auth/service/RateLimitService.java`: Bucket4j `ConcurrentHashMap<String, Bucket>` keyed by IP; 5-token bucket refilling 5 tokens per 10 minutes (Caffeine cache, 10-min TTL); `tryConsume(ip)` returns `true` if allowed
- [X] T023 [US1] Implement `AuthService` at `implementation/services/entry-auth-service/src/main/java/com/privchat/auth/service/AuthService.java`: `join(username, password, ip, session)` — trim username, validate length ≤ 64, check rate limit, compare password against `NETWORK_PASSWORD` env var, set `session.setAttribute("username", username)` + `authenticated=true`, persist `SecurityAuditLog`; throw typed exceptions for each failure case
- [X] T024 [US1] Implement `AuthController` at `implementation/services/entry-auth-service/src/main/java/com/privchat/auth/controller/AuthController.java`: `POST /auth/join` with `@RequestBody JoinRequest record`; extract client IP from `X-Forwarded-For` header; map exceptions to HTTP 400/401/429; return `JoinResponse record`; add `Retry-After` header on 429
- [X] T025 [P] [US1] Create `JoinRequest` and `JoinResponse` Java records at `implementation/services/entry-auth-service/src/main/java/com/privchat/auth/controller/dto/JoinRequest.java` and `JoinResponse.java`
- [X] T026 [P] [US1] Create `authApi.ts` at `implementation/frontend/src/lib/authApi.ts`: `joinNetwork(username, password)` async function calling `POST /auth/join`; returns `{username}` on success; throws typed error with `status` and `message` fields
- [X] T027 [P] [US1] Create `JoinForm` component at `implementation/frontend/src/components/JoinForm/JoinForm.tsx`: username text field (trim + max 64 char validation), password field, "Join network" button; loading state disables button + shows spinner; error state displays message below form; on HTTP 429 parse `Retry-After` response header (seconds) and display "Too many attempts — try again in N minutes" (FR-009); `onSuccess` callback
- [X] T028 [US1] Create entry gate page at `implementation/frontend/src/app/page.tsx`: SSR page renders `JoinForm`; on `joinNetwork` success, save username to `localStorage` key `privchat_username`, redirect to `/portal`
- [X] T029 [P] [US1] Create portal interior placeholder page at `implementation/frontend/src/app/portal/page.tsx`: displays "Welcome, {username}" (read from session); will be replaced in future features
- [X] T030 [P] [US1] Configure Next.js rewrites in `implementation/frontend/next.config.ts`: rewrite `/auth/:path*` → `http://api-gateway:8080/auth/:path*` (Docker internal); add security headers via `headers()`: `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin`

**Checkpoint**: User Story 1 fully functional. All T018–T021 tests pass. Run quickstart.md §4–7 validation scenarios.

---

## Phase 4: User Story 2 — Returning to the Portal (Priority: P2)

**Goal**: User with a valid active session navigates to the portal URL and bypasses the entry gate. Expired session redirects back to gate with username pre-filled.

**Independent Test**: Join the network (US1), close tab, reopen http://localhost:3000 → land directly on `/portal`. Clear session cookie, reopen → land on entry gate with username pre-filled, password empty.

### Tests for User Story 2 ⚠️ WRITE AND CONFIRM FAILING BEFORE T035

- [X] T031 [P] [US2] Add failing unit tests to `AuthServiceTest` at `implementation/services/entry-auth-service/src/test/java/com/privchat/auth/service/AuthServiceTest.java` (extending the class created in T019): `checkSession(session)` returns `{username, authenticated:true}` when session has `authenticated=true`; returns `{authenticated:false}` when session missing/expired
- [X] T032 [P] [US2] Write failing unit test for `AuthController GET /auth/session` and `DELETE /auth/session` in `implementation/services/entry-auth-service/src/test/java/com/privchat/auth/controller/AuthControllerTest.java`: 200 with `{username, authenticated:true}` on valid session; 401 on no session; `DELETE` returns 200 and invalidates session
- [X] T033 [P] [US2] Write failing integration test `SessionPersistenceIntegrationTest` in `implementation/services/entry-auth-service/src/test/java/com/privchat/auth/integration/SessionPersistenceIntegrationTest.java`: join → capture session cookie → `GET /auth/session` with cookie → assert 200; `DELETE /auth/session` → assert session row removed from DB; expired session → assert 401

### Implementation for User Story 2

- [X] T034 [US2] Add `checkSession(HttpSession)` and `logout(HttpSession)` to `AuthService` at `implementation/services/entry-auth-service/src/main/java/com/privchat/auth/service/AuthService.java`
- [X] T035 [US2] Add `GET /auth/session` and `DELETE /auth/session` to `AuthController` at `implementation/services/entry-auth-service/src/main/java/com/privchat/auth/controller/AuthController.java`: `GET` returns `SessionResponse record`; `DELETE` calls `session.invalidate()`, clears cookie, returns 200
- [X] T036 [P] [US2] Create `SessionResponse` Java record at `implementation/services/entry-auth-service/src/main/java/com/privchat/auth/controller/dto/SessionResponse.java`
- [X] T037 [US2] Add session check to entry gate page at `implementation/frontend/src/app/page.tsx`: (a) **Server component**: on server render call `GET /auth/session` (forward session cookie via `headers()`); if `authenticated=true` issue `redirect('/portal')`; if expired/missing render `JoinForm` without `defaultUsername` (localStorage is unavailable server-side); (b) **Client hydration**: wrap page in a `'use client'` child component or add a `useEffect` that reads `localStorage.getItem('privchat_username')` after mount and sets `defaultUsername` prop on `JoinForm`
- [X] T038 [P] [US2] Add `checkSession()` and `logout()` functions to `implementation/frontend/src/lib/authApi.ts`
- [X] T039 [P] [US2] Verify that the wildcard rewrite `/auth/:path*` added in T030 correctly forwards `GET /auth/session` and `DELETE /auth/session` to the gateway — no new rewrite rules are needed; confirm by running `curl -v http://localhost:3000/auth/session` and checking the request reaches the gateway
- [X] T040 [US2] Add unauthenticated redirect guard to portal interior page at `implementation/frontend/src/app/portal/page.tsx`: server-side `GET /auth/session`; if `authenticated=false` redirect to `/`

**Checkpoint**: User Stories 1 AND 2 both independently functional. All T031–T033 tests pass. Run quickstart.md §8 validation scenario.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Hardening, developer experience, and end-to-end validation

- [X] T041 [P] Add `implementation/services/entry-auth-service/src/test/resources/application-test.yml` to configure Testcontainers PostgreSQL for integration tests; add `testcontainers` and `postgresql` test dependencies to `build.gradle`
- [X] T042 [P] Add `logback-spring.xml` at `implementation/services/entry-auth-service/src/main/resources/logback-spring.xml`: structured JSON logging to stdout; include `ip`, `eventType`, `username` MDC fields for security events
- [X] T043 [P] Add Spring Boot Actuator health endpoint configuration to `implementation/services/entry-auth-service/src/main/resources/application.yml`: expose `health` only; disable all other endpoints
- [X] T044 [P] Add Gradle wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) to `implementation/services/entry-auth-service/` and `implementation/api-gateway/` so Docker builds are hermetic
- [X] T045 Run full quickstart.md validation end-to-end: `docker compose up --build`, test all scenarios in §4–8; confirm all pass
- [X] T046 [P] Update `implementation/.env.example` with any additional variables discovered during implementation

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately; T002–T007 all parallel
- **Foundational (Phase 2)**: Depends on Phase 1 — T008 first (migration), then T009–T017 parallel
- **User Story 1 (Phase 3)**: Depends on Phase 2 complete — T018–T021 (tests) first, then T022–T030
- **User Story 2 (Phase 4)**: Depends on Phase 2 complete — T031–T033 (tests) first, then T034–T040; integrates with US1 frontend files
- **Polish (Phase 5)**: Depends on all desired user stories complete

### Within Each User Story

1. ⚠️ Tests MUST be written and confirmed **failing** before implementation (constitution Principle III)
2. Backend: entity/model → service → controller → DTO
3. Frontend: API client → component → page
4. Integration before marking story done

### Parallel Opportunities

```bash
# Phase 1 — all parallel after T001:
T002 entry-auth-service Gradle init
T003 api-gateway Gradle init
T004 Next.js init
T005 docker-compose.yml
T006 docker-compose.override.yml
T007 .env.example

# Phase 2 — T008 first, then all parallel:
T009 SecurityAuditLog entity     T010 Repository
T011 SessionConfig               T012 SecurityConfig
T013 application.yml             T014 Gateway routing
T015 auth-service Dockerfile     T016 gateway Dockerfile     T017 frontend Dockerfile

# Phase 3 — tests parallel first:
T018 RateLimitServiceTest    T019 AuthServiceTest
T020 AuthControllerTest      T021 JoinIntegrationTest
# then backend parallel:
T022 RateLimitService        T025 DTOs
T023 AuthService (after T022)
T024 AuthController (after T023)
# then frontend parallel:
T026 authApi.ts   T027 JoinForm   T030 next.config.ts
T028 page.tsx (after T027)   T029 portal placeholder
```

---

## Implementation Strategy

### MVP: User Story 1 Only

1. Complete Phase 1 (Setup)
2. Complete Phase 2 (Foundational) — CRITICAL blocker
3. Complete Phase 3 (US1) — write tests first, confirm failing, implement
4. **STOP AND VALIDATE**: all US1 quickstart scenarios pass
5. Commit: `feat: entry gate — join network (US1 MVP)`

### Full Delivery

1. MVP (above)
2. Phase 4 (US2) — session persistence + username pre-fill
3. Phase 5 (Polish)
4. Commit: `feat: entry gate — session persistence + polish`

---

## Notes

- `[P]` tasks operate on different files; safe to parallelise
- TDD is mandatory per constitution — never start `T022+` before `T018–T021` are red
- `NETWORK_PASSWORD` is injected via env var; never hardcoded
- Bucket4j rate limit state is in-memory; resets on `entry-auth-service` restart (acceptable v1 behaviour)
- Next.js rewrites proxy `/auth/**` to the gateway **inside Docker**; browser always hits port 3000
