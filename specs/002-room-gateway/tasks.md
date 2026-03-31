# Tasks: Room Gateway

**Feature**: `002-room-gateway` | **Branch**: `002-room-gateway`
**Input**: spec.md (3 user stories), plan.md, data-model.md, contracts/, research.md
**Stack**: Java 25, Spring Boot 4.0.4, Gradle, jOOQ 3.20.4, Flyway 11.20.3, JJWT 0.12.6, Next.js (TypeScript)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (no dependency on an incomplete preceding task)
- **[Story]**: User story this task belongs to (US1, US2, US3)
- Exact file paths included in each task description

---

## Phase 1: Setup — rooms-service Scaffold

**Purpose**: Create the new microservice skeleton. Must complete before any domain work.

- [ ] T001 Create `implementation/services/rooms-service/build.gradle` with Spring Boot 4.0.4, Spring Web, Spring Security, jOOQ 3.20.4, Flyway 11.20.3, JJWT 0.12.6, Testcontainers, PostgreSQL driver — mirror `entry-auth-service/build.gradle` dependency versions exactly
- [ ] T002 [P] Create `implementation/services/rooms-service/Dockerfile` — mirror `entry-auth-service/Dockerfile` exactly (Java 25, multi-stage Gradle build)
- [ ] T003 [P] Create `implementation/services/rooms-service/src/main/java/com/privchat/rooms/RoomsServiceApplication.java` (Spring Boot main class) and `src/main/resources/application.yml` with datasource (`SPRING_DATASOURCE_*`), Flyway, jOOQ dialect, server port 8080, `ENTRY_AUTH_SERVICE_URL` env var, `JWT_EXPIRY_SECONDS` default 900
- [ ] T004 Update `implementation/docker-compose.yml`: add `postgres-rooms` service (PostgreSQL 17, separate volume `postgres_rooms_data`, env vars `ROOMS_DB`/`ROOMS_DB_USER`/`ROOMS_DB_PASSWORD`, healthcheck); add `rooms-service` service (depends on `postgres-rooms` healthy + `entry-auth-service` healthy, env `ENTRY_AUTH_SERVICE_URL`, no published port, on `privchat-net`); add `ROOMS_SERVICE_URL` env var to `api-gateway` service

---

## Phase 2: Foundational — JWT Infrastructure + DB Schema

**Purpose**: Auth layer and database schema that block every user story. Must complete before Phase 3+.

- [ ] T005 Create `implementation/services/rooms-service/src/main/resources/db/migration/V1__create_rooms.sql`: `rooms` table with columns `id BIGSERIAL PK`, `name VARCHAR(100) UNIQUE NOT NULL`, `creator_username VARCHAR(64) NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `active_member_count INT NOT NULL DEFAULT 0 CHECK(>=0)`; indexes on `(created_at DESC)` and `(creator_username)`
- [ ] T006 [P] Create `implementation/services/rooms-service/src/main/resources/db/migration/V2__create_user_room_stats.sql`: `user_room_stats` table with `username VARCHAR(64) PK`, `rooms_created_count INT NOT NULL DEFAULT 0 CHECK(>=0)`, `active_rooms_count INT NOT NULL DEFAULT 0 CHECK(>=0 AND <=10)`
- [ ] T007 [P] Create `implementation/services/rooms-service/src/main/resources/db/migration/V3__create_room_audit_log.sql`: `room_audit_log` table with `id BIGSERIAL PK`, `event_type VARCHAR(50) NOT NULL CHECK IN ('CREATE_ROOM','UPDATE_ROOM','DELETE_ROOM','UNAUTHORIZED_ATTEMPT')`, `room_id BIGINT NULLABLE`, `room_name VARCHAR(100) NULLABLE`, `actor_username VARCHAR(64) NOT NULL`, `occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`; indexes on `(actor_username, occurred_at DESC)` and `(event_type, occurred_at DESC)`
- [ ] T008 Configure jOOQ DDL-based code generation in `implementation/services/rooms-service/build.gradle` using `jooq-meta-extensions`: point to V1–V3 migration SQL files, output package `com.privchat.rooms.jooq`, dialect POSTGRES — mirror `entry-auth-service` jOOQ codegen config exactly
- [ ] T009 [P] Create Java record models: `implementation/services/rooms-service/src/main/java/com/privchat/rooms/model/Room.java`, `UserRoomStats.java`, `RoomAuditLog.java` — fields match data-model.md column definitions
- [ ] T010 Add JJWT 0.12.6 dependencies to `implementation/services/entry-auth-service/build.gradle`: `implementation("io.jsonwebtoken:jjwt-api:0.12.6")`, `runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")`, `runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")`
- [ ] T010a Write failing unit test `implementation/services/entry-auth-service/src/test/java/com/privchat/auth/service/JwtServiceTest.java`: tests must cover `generateToken(username)` returning a non-null RS256 JWT, `getPublicKey()` returning non-null RSAPublicKey, and ephemeral key pair generation when env vars are blank. Run test — must fail (class doesn't exist yet).
- [ ] T011 Create `implementation/services/entry-auth-service/src/main/java/com/privchat/auth/service/JwtService.java`: `@Service`; loads `JWT_PRIVATE_KEY` (base64 PKCS#8 PEM) + `JWT_PUBLIC_KEY` (base64 X.509 PEM) from env — if blank, generates ephemeral RSA 2048 key pair at startup; `generateToken(String username)` signs RS256 JWT with `sub=username`, `iat=now`, `exp=now+JWT_EXPIRY_SECONDS`; `getPublicKey()` returns `RSAPublicKey`
- [ ] T011rev **Security Review Gate — JwtService (entry-auth)**: second-developer reviews `JwtService.java` before T012 proceeds. Checklist: RSA key pair loaded correctly, private key not logged, ephemeral mode documented, JWT expiry enforced, no hardcoded secrets. Sign off in PR review before merging T011.
- [ ] T012 Create `implementation/services/entry-auth-service/src/main/java/com/privchat/auth/controller/JwksController.java`: `@RestController`; `GET /auth/jwks` returns `{ "keys": [{ "kty":"RSA","use":"sig","alg":"RS256","kid":"priv-chat-1","n":"<base64url modulus>","e":"AQAB" }] }` using `JwtService.getPublicKey()`; endpoint must be unauthenticated
- [ ] T013 Update `implementation/services/entry-auth-service/src/main/java/com/privchat/auth/controller/dto/JoinResponse.java` to `record JoinResponse(String username, String token)`; update `implementation/services/entry-auth-service/src/main/java/com/privchat/auth/service/AuthService.java` to call `JwtService.generateToken(username)` and include the token in the returned `JoinResponse`
- [ ] T014 Add `GET /auth/refresh-token` to `implementation/services/entry-auth-service/src/main/java/com/privchat/auth/controller/AuthController.java`: requires valid session (use existing session validation); returns `{ "token": "..." }` with a fresh JWT via `JwtService.generateToken(username)`; returns 401 if no valid session
- [ ] T015 Update `implementation/services/entry-auth-service` Spring Security config to permit `GET /auth/jwks` without authentication (add `requestMatchers("/auth/jwks").permitAll()` before existing rules)
- [ ] T015a Write failing unit test `implementation/services/rooms-service/src/test/java/com/privchat/rooms/security/JwksClientTest.java`: mock JWKS JSON response, assert `getPublicKey()` returns a valid RSAPublicKey with correct modulus/exponent. Run test — must fail.
- [ ] T016 Create `implementation/services/rooms-service/src/main/java/com/privchat/rooms/security/JwksClient.java`: `@Component`; at startup (via constructor or `@PostConstruct`), calls `GET {ENTRY_AUTH_SERVICE_URL}/auth/jwks` using `RestClient`, parses JWKS JSON, reconstructs `RSAPublicKey` from `n` and `e` fields using `RSAPublicKeySpec`; caches key in volatile field; exposes `getPublicKey()`
- [ ] T016a Write failing unit test `implementation/services/rooms-service/src/test/java/com/privchat/rooms/security/JwtServiceTest.java`: test valid RS256 token returns username; expired token throws; tampered signature throws; missing `sub` throws. Run test — must fail.
- [ ] T017 Create `implementation/services/rooms-service/src/main/java/com/privchat/rooms/security/JwtService.java`: `@Service`; `validateToken(String token)` — parses JWT using JJWT, verifies RS256 signature with `JwksClient.getPublicKey()`, checks expiry; returns username (`sub` claim) on success; throws on invalid/expired token
- [ ] T017a Write failing unit test `implementation/services/rooms-service/src/test/java/com/privchat/rooms/security/JwtAuthFilterTest.java`: test missing Authorization header → 401; malformed token → 401; valid token → SecurityContext populated with username. Run test — must fail.
- [ ] T018 Create `implementation/services/rooms-service/src/main/java/com/privchat/rooms/security/JwtAuthFilter.java`: extends `OncePerRequestFilter`; extracts `Authorization: Bearer <token>` header; calls `JwtService.validateToken()`; on success sets `UsernamePasswordAuthenticationToken` in `SecurityContextHolder`; on failure writes 401 JSON response `{ "error": "Authentication required" }`
- [ ] T019 Create `implementation/services/rooms-service/src/main/java/com/privchat/rooms/security/SecurityConfig.java`: `@Configuration @EnableWebSecurity`; `SessionCreationPolicy.STATELESS`; permit `/actuator/health`, authenticate all other requests; `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`; disable CSRF, formLogin, httpBasic
- [ ] T019rev **Security Review Gate — rooms-service auth stack (T016–T019)**: second-developer reviews `JwksClient.java`, `JwtService.java`, `JwtAuthFilter.java`, `SecurityConfig.java` before T020 proceeds. Checklist: RSAPublicKey reconstruction correct, no timing oracle in JWT validation, stateless session confirmed, all endpoints authenticated except `/actuator/health`. Sign off in PR review.
- [ ] T020 Create `implementation/api-gateway/src/main/java/com/privchat/gateway/proxy/RoomsProxyController.java`: `@RestController @RequestMapping("/rooms/**")`; mirrors `AuthProxyController` exactly — injects `RestClient` configured with `${services.rooms.url}`; forwards all headers except `Host` and `Content-Length` including `Authorization: Bearer`; proxies all HTTP methods
- [ ] T021 Update `implementation/api-gateway/src/main/resources/application.yml` to add `services.rooms.url: ${ROOMS_SERVICE_URL:http://rooms-service:8080}` alongside existing `services.auth.url`

---

## Phase 3: User Story 1 — Browse and Join Public Rooms

**Story goal**: Logged-in user sees all public rooms and can click Join to enter one.
**Independent test**: Log in → see room list with cards showing name/creator/timestamp/count → click Join → confirm navigation to room view.

- [ ] T022 [P] [US1] Create `implementation/services/rooms-service/src/main/java/com/privchat/rooms/controller/dto/RoomResponse.java`: Java record with fields `id`, `name`, `creatorUsername`, `createdAt` (ISO-8601 string), `activeMemberCount`; maps from `Room` model
- [ ] T022a [P] [US1] Write failing integration test `implementation/services/rooms-service/src/test/java/com/privchat/rooms/repository/RoomRepositoryTest.java` (Testcontainers + postgres-rooms): assert `findAll()` returns rooms ordered newest-first; `findById()` returns correct room; missing ID returns empty Optional. Run test — must fail.
- [ ] T023 [P] [US1] Create `implementation/services/rooms-service/src/main/java/com/privchat/rooms/repository/RoomRepository.java`: `@Repository`; `findAll()` returns all rooms ordered by `created_at DESC` using jOOQ DSL; `findById(Long id)` returns `Optional<Room>`; all queries use jOOQ parameterized DSL (no raw SQL string concatenation)
- [ ] T024 [US1] Create `implementation/services/rooms-service/src/main/java/com/privchat/rooms/controller/RoomController.java` with `GET /rooms` (returns `List<RoomResponse>`, 200 OK, `[]` when empty) and `GET /rooms/{id}` (returns `RoomResponse` or 404 JSON error); inject `RoomRepository`; map `Room` → `RoomResponse`
- [ ] T025 [US1] Create `implementation/frontend/src/lib/authContext.tsx`: React context + provider storing JWT token string in memory (React state — NOT localStorage); expose `{ token, setToken }` via `useAuth()` hook; wrap app layout so all pages can access it
- [ ] T026 [US1] Create `implementation/frontend/src/lib/roomsApi.ts`: typed API client functions `fetchRooms()`, `fetchRoom(id)`, `createRoom(name?)`, `updateRoom(id, name)`, `deleteRoom(id)` — each calls the appropriate `/rooms/**` endpoint via `fetch`, includes `Authorization: Bearer ${token}` header from `useAuth()`, returns typed response or throws on non-2xx
- [ ] T027 [US1] Update `implementation/frontend/src/app/portal/EntryGateClient.tsx` (or wherever `JoinResponse` is handled after login) to call `authContext.setToken(response.token)` storing the JWT in memory; redirect to `/portal/rooms` on successful login
- [ ] T028 [P] [US1] Create `implementation/frontend/src/components/RoomCard/index.tsx`: functional component accepting `room: RoomResponse`; displays name, creatorUsername, createdAt (formatted), activeMemberCount, and a "Join" button; clicking Join calls `onJoin(room.id)` prop
- [ ] T029 [US1] Create `implementation/frontend/src/app/portal/rooms/page.tsx`: Room Gateway page; fetches room list via `fetchRooms()` on mount; renders list of `<RoomCard>`; handles Join click (navigate to `/portal/rooms/[id]`); shows loading state; redirects to `/` if no JWT in context (unauthenticated guard)

---

## Phase 4: User Story 2 — Create Room with Default Values

**Story goal**: User clicks "Create Room" → room created as `{username}-room-{n}` → user enters it immediately.
**Independent test**: Click "Create Room" → new room appears in list with correct name pattern → user navigated into room.

- [ ] T030 [P] [US2] Create `implementation/services/rooms-service/src/main/java/com/privchat/rooms/controller/dto/CreateRoomRequest.java`: Java record with optional `name` field (may be null); and `UpdateRoomRequest.java` with required `name` field
- [ ] T031 [P] [US2] Create `implementation/services/rooms-service/src/main/java/com/privchat/rooms/repository/AuditLogRepository.java`: `@Repository`; `insert(String eventType, Long roomId, String roomName, String actorUsername)` writes one append-only row to `room_audit_log` using jOOQ DSL
- [ ] T031a [US2] Write failing unit test `implementation/services/rooms-service/src/test/java/com/privchat/rooms/service/RoomServiceTest.java`: test cap enforcement (`active_rooms_count == 10` → `RoomCapException`); naming sequence (`rooms_created_count = 2` → name `alice-room-3`); custom name uniqueness conflict → 409; successful creation → correct Room returned. Run test — must fail.
- [ ] T032 [US2] Create `implementation/services/rooms-service/src/main/java/com/privchat/rooms/service/RoomService.java`: `createRoom(String username, @Nullable String customName)` — within a single DB transaction: (1) upsert `user_room_stats` row for username; (2) check `active_rooms_count < 10`, throw `RoomCapException` if not; (3) resolve name: use `customName` if provided (check uniqueness → 409), else generate `{username}-room-{rooms_created_count+1}` (increment until unique); (4) increment both counters in `user_room_stats`; (5) insert into `rooms`; (6) write `CREATE_ROOM` audit log entry; return created `Room`
- [ ] T033 [US2] Add `POST /rooms` to `implementation/services/rooms-service/src/main/java/com/privchat/rooms/controller/RoomController.java`: accepts `CreateRoomRequest`, calls `RoomService.createRoom()`, returns 201 with `RoomResponse`; handle `RoomCapException` → 422; handle name conflict → 409; handle validation → 400
- [ ] T034 [US2] Add "Create Room" button to `implementation/frontend/src/app/portal/rooms/page.tsx`: calls `createRoom()` from `roomsApi.ts`; on 201 success, navigates user to `/portal/rooms/[id]` of the new room; on 422 response, stores cap-reached state in component state (does not throw)
- [ ] T035 [US2] Disable "Create Room" button and show cap message in `implementation/frontend/src/app/portal/rooms/page.tsx` when cap-reached state is set (set on 422 from POST, or infer by counting rooms where `creatorUsername === currentUser`); button re-enables only if active rooms drop below 10 in the future

---

## Phase 5: User Story 3 — Empty State

**Story goal**: When no rooms exist, user sees a helpful message and a visible "Create Room" button.
**Independent test**: Access Room Gateway with empty DB → see empty-state message and working "Create Room" button.

- [ ] T036 [P] [US3] Create `implementation/frontend/src/components/EmptyState/index.tsx`: functional component; displays "No rooms yet — create one to get started" message and a "Create Room" button (calls `onCreateRoom` prop); styled to be prominent
- [ ] T037 [US3] Update `implementation/frontend/src/app/portal/rooms/page.tsx` to render `<EmptyState onCreateRoom={...} />` when room list is empty (`rooms.length === 0`) in place of the room list; "Create Room" in EmptyState calls the same create handler as the main button

---

## Phase 6: User Story 4 — Room Management (Rename and Delete)

**Story goal**: Creator can rename or delete their own rooms. Non-creators cannot. Deletion frees a cap slot.
**Independent test**: As creator — rename a room, verify new name in list. Delete a room, verify it disappears and cap slot is freed. As non-creator — attempt both actions via API and verify 403 + audit log.

- [ ] T038 [P] [US4] Add `PUT /rooms/{id}` to `implementation/services/rooms-service/src/main/java/com/privchat/rooms/controller/RoomController.java` + `implementation/services/rooms-service/src/main/java/com/privchat/rooms/service/RoomService.java`: accepts `UpdateRoomRequest`; verifies JWT `sub` matches `creator_username` (→ 403 + `UNAUTHORIZED_ATTEMPT` audit log entry via `AuditLogRepository` if not); validates name non-empty and ≤ 100 chars (→ 400); checks global name uniqueness (→ 409); updates `rooms.name`; writes `UPDATE_ROOM` audit log; returns updated `RoomResponse`
- [ ] T039 [P] [US4] Add `DELETE /rooms/{id}` to `implementation/services/rooms-service/src/main/java/com/privchat/rooms/controller/RoomController.java` + `implementation/services/rooms-service/src/main/java/com/privchat/rooms/service/RoomService.java`: verifies creator (→ 403 + `UNAUTHORIZED_ATTEMPT` audit log if not); in a single transaction: deletes room, decrements `user_room_stats.active_rooms_count`, writes `DELETE_ROOM` audit log entry with `room_name` snapshot; returns 204
- [ ] T040 [US4] Add inline rename UI to `implementation/frontend/src/components/RoomCard/index.tsx`: show "Rename" button only when `room.creatorUsername === currentUser`; clicking opens an inline text input pre-filled with current name; on submit calls `updateRoom(room.id, newName)` from `roomsApi.ts`; on 200 refreshes room in list; on 409 shows "Name already taken" error inline
- [ ] T041 [US4] Add delete UI to `implementation/frontend/src/components/RoomCard/index.tsx`: show "Delete" button only when `room.creatorUsername === currentUser`; clicking shows a confirmation prompt ("Delete this room?"); on confirm calls `deleteRoom(room.id)` from `roomsApi.ts`; on 204 removes room from list in state; if user was at cap, re-enables "Create Room" button

---

## Final Phase: Polish & Cross-Cutting Concerns

- [ ] T042 Implement `UNAUTHORIZED_ATTEMPT` audit log writes in `implementation/services/rooms-service/src/main/java/com/privchat/rooms/service/RoomService.java` for all 403 code paths (PUT and DELETE non-creator attempts) using `AuditLogRepository`
- [ ] T043 Add JWT refresh logic to `implementation/frontend/src/lib/authContext.tsx` (or a dedicated hook): check remaining JWT lifetime on each rooms API call; if < 60 seconds, call `GET /auth/refresh-token` (using session cookie) and update `token` in context before retrying the rooms API call
- [ ] T044 Add real-time room list updates to `implementation/frontend/src/app/portal/rooms/page.tsx`: poll `GET /rooms` every 2 seconds (or implement SSE if supported) so newly created/deleted rooms appear within 2 seconds (FR-003, SC-003) without a full page refresh; cancel polling on component unmount

---

## Dependencies

```
T001 → T002, T003 (can start after scaffold)
T004 depends on T001 (needs service name)
T005–T007 can start after T001
T008 depends on T005, T006, T007 (needs all migration files)
T009 can start after T005 (model fields from migrations)
T010 can start immediately (separate service, build.gradle only)
T011 depends on T010 (needs JJWT dep)
T012 depends on T011 (needs JwtService.getPublicKey())
T013 depends on T011 (needs JwtService.generateToken())
T014 depends on T011
T015 depends on T012 (needs /auth/jwks route to exist)
T016 depends on T011, T012 (needs JWKS endpoint design finalized)
T017 depends on T016 (needs JwksClient)
T018 depends on T017 (needs JwtService.validateToken())
T019 depends on T018 (needs JwtAuthFilter)
T020 can start after T001 (just proxy wiring)
T021 can start after T020

T022, T023 can start after T008+T009 (need jOOQ + models)
T024 depends on T022, T023
T025 can start after T001 (React context, no backend dep)
T026 depends on T025 (needs useAuth hook for token)
T027 depends on T025 (needs authContext.setToken)
T028 can start after T022 (needs RoomResponse type shape)
T029 depends on T024, T026, T027, T028, T019 (needs controller live)

T030, T031 can start after T009
T032 depends on T030, T031 (needs RoomService deps)
T033 depends on T032, T019
T034 depends on T033, T026
T035 depends on T034

T036 no backend dep (pure UI)
T037 depends on T036, T034

T038, T039 depend on T033 (need RoomService + AuditLogRepository)
T040 depends on T038 (needs PUT endpoint live), T028 (needs RoomCard)
T041 depends on T039 (needs DELETE endpoint live), T028 (needs RoomCard)
T042 depends on T031 (needs AuditLogRepository)
T043 depends on T025 (needs AuthContext)
T044 depends on T029 (needs rooms page)
```

## Parallel Execution Examples

**Entry-auth JWT work** (T010–T015) can run entirely in parallel with **rooms-service scaffold** (T001–T009).

**Within US1**: T022, T023, T025, T028 can all run in parallel once T008+T009 are done.

**Within US4**: T038 + T039 run in parallel (different endpoints); T040 + T041 run in parallel (different UI interactions in same component).

**Frontend work** (T025 onward) can start early — React context and API client have no backend runtime dependency (only type shape).

## Implementation Strategy

**MVP = Phase 3 (US1) complete**: User can log in, see the room list with full room cards, and click Join. This delivers SC-001 and FR-001/FR-002/FR-004/FR-009.

**Increment 2 = Phase 4 (US2)**: Add room creation. Delivers SC-002, FR-005/FR-006/FR-007/FR-012.

**Increment 3 = Phase 5 (US3)**: Empty state polish. Delivers FR-008.

**Increment 4 = Phase 6 (US4)**: Rename + delete with creator-only enforcement. Delivers FR-013/FR-014/FR-015. Frees cap slots on delete.

**Increment 5 = Final Phase**: JWT refresh, real-time updates.

---

**Total tasks**: 54 (44 implementation + 7 TDD test tasks + 2 security review gates + 1 dependency audit)
**Tasks per phase**: Setup 4 | Foundational 27 | US1 9 | US2 7 | US3 2 | US4 4 | Polish 3
**Parallel opportunities**: 20 tasks marked [P]
