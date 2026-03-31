# Research: Room Gateway

**Feature**: 002-room-gateway
**Date**: 2026-03-31
**Phase**: Phase 0 — Technology Research

---

## 1. Authentication Strategy: Shared Spring Session JDBC

**Decision**: `rooms-service` configures `spring-session-jdbc` pointing to the same
PostgreSQL instance as `entry-auth-service`. Spring Security's session filter chain
resolves the `SESSION` cookie against the shared `SPRING_SESSION` tables automatically.
No direct HTTP calls from `rooms-service` to `entry-auth-service` are needed.

**Rationale**: The user session is a PostgreSQL row. Both services share the database.
Sharing the session store is the simplest approach that satisfies the authentication
requirement: zero additional network hops, zero coupling to `entry-auth-service`'s HTTP
API, and automatic session extension on every rooms request (rolling expiry).

**How it works**:
1. `entry-auth-service` creates the session row on `POST /auth/join` and sets the
   `SESSION` HttpOnly cookie.
2. The gateway proxy (`RoomsProxyController`) forwards all headers including `Cookie`
   to `rooms-service` — identical to how `AuthProxyController` works today.
3. `rooms-service` Spring Security intercepts the request, finds the `SESSION` cookie,
   looks up the session row in PostgreSQL, validates expiry, and injects a
   `UsernamePasswordAuthenticationToken` principal for the request.
4. Controller methods read the authenticated username via
   `SecurityContextHolder.getContext().getAuthentication().getName()`.

**Spring Security principal resolution**: Spring Session JDBC stores `PRINCIPAL_NAME`
in the `SPRING_SESSION` table — this is the `username` set by `entry-auth-service` at
join time. Spring Security uses this as the principal name automatically when
`@EnableJdbcHttpSession` is active.

**Alternatives Considered**:
- **HTTP call: rooms-service → entry-auth-service GET /auth/session per request**:
  Adds a network round-trip on every request. Introduces a failure mode (entry-auth-
  service unavailable → all room operations fail). More coupling. Rejected: YAGNI.
- **API Gateway pre-auth filter**: Gateway validates session before forwarding.
  Adds complexity to the gateway (currently a dumb proxy). Rooms-service would need
  to trust an `X-Username` header. Adds a gateway-specific security dependency.
  Rejected: more complex than shared session store for the same outcome.
- **JWT tokens**: Stateless. Constitution explicitly rejects stateless JWTs (cannot
  be invalidated server-side). Rejected: constitution violation.

**Key Notes**:
- `rooms-service` must use the same `spring.session.jdbc` configuration as
  `entry-auth-service` (same `SESSION` cookie name, same table names).
- `rooms-service` does NOT call `JdbcIndexedSessionRepository.setDefaultMaxInactive
  Interval()` — session lifetime is managed exclusively by `entry-auth-service`.
- The `SessionConfig` in rooms-service sets `SESSION_COOKIE_SECURE` from env var
  (same pattern) but does NOT override `setDefaultMaxInactiveInterval`.

---

## 2. API Gateway: RoomsProxyController

**Decision**: Add `RoomsProxyController` to the existing gateway, mirroring the
`AuthProxyController` pattern exactly. Map `@RequestMapping("/rooms/**")` to
`rooms-service` via a `RestClient` configured with `ROOMS_SERVICE_URL` env var.

**Rationale**: The gateway already uses a custom RestClient proxy pattern rather than
Spring Cloud Gateway. The rooms proxy is a copy-paste of AuthProxyController with the
base URL and path prefix swapped. Zero new dependencies needed in the gateway.

**Configuration addition in `application.yml`**:
```yaml
services:
  auth:
    url: ${ENTRY_AUTH_SERVICE_URL:http://entry-auth-service:8080}
  rooms:
    url: ${ROOMS_SERVICE_URL:http://rooms-service:8080}
```

**Cookie forwarding**: The proxy forwards the `Cookie` header unchanged, which carries
the `SESSION` cookie to `rooms-service` for authentication resolution.

---

## 3. jOOQ Code Generation for rooms-service

**Decision**: Same jOOQ 3.20.4 + `nu.studer.jooq` Gradle plugin configuration as
`entry-auth-service`. Use Flyway migrations to create the schema before jOOQ generates.
Use `org.jooq:jooq-meta-extensions` for offline DDL-based generation (no live DB needed
during build).

**Key configuration**:
```groovy
jooq {
    version = '3.20.4'
    configurations {
        main {
            generationTool {
                generator {
                    database {
                        name = 'org.jooq.meta.extensions.ddl.DDLDatabase'
                        properties {
                            property { key = 'scripts'; value = 'src/main/resources/db/migration' }
                            property { key = 'sort'; value = 'flyway' }
                        }
                    }
                    target {
                        packageName = 'com.privchat.rooms.jooq'
                        directory   = 'build/generated-sources/jooq'
                    }
                }
            }
        }
    }
}
```

---

## 4. Room Naming Sequence & Creation Cap

**Decision**: Track per-user state in a `user_room_stats` table with two counters:
- `rooms_created_count` — monotonically increasing, used to compute the next name
  (`{username}-room-{rooms_created_count + 1}`), never decremented even on delete
- `active_rooms_count` — incremented on create, decremented on delete, capped at 10

**Rationale**: Separating the naming counter from the active count ensures names are
unique and never reused (e.g., `alice-room-3` is never re-created after deletion).
The cap check uses `active_rooms_count` so deleted rooms free up a slot.

**Name collision handling** (per edge case in spec): After generating the default name
`{username}-room-{n}`, check for uniqueness in the `rooms` table. If the name exists
(e.g., due to a manual rename of another room to that name), increment `n` until a
free name is found. This loop is bounded by the 10-room cap.

**Atomic operation**: The `RoomService.createRoom()` method executes the following in
a single database transaction:
1. `INSERT ... ON CONFLICT DO NOTHING` into `user_room_stats` to ensure row exists
2. Check `active_rooms_count < 10` (throw `RoomLimitExceededException` if not)
3. Find next available name
4. `INSERT` into `rooms`
5. `UPDATE user_room_stats SET rooms_created_count = rooms_created_count + 1,
   active_rooms_count = active_rooms_count + 1`
6. `INSERT` into `room_audit_log`

---

## 5. Occupant Count

**Decision**: `rooms.active_member_count` is a denormalized `INT` column defaulting
to 0. It is set to 0 at room creation and reserved for update by the future
messaging/chat feature when users enter or leave rooms. For this feature, all rooms
show 0 occupants.

**Rationale**: The spec (clarification Q3) requires occupant count in the room card.
The rooms-service owns room metadata. Tracking real-time occupancy requires the chat
feature (out of scope here). The column is present and ready; the update mechanism
will be defined in the chat feature's plan.

---

## 6. Docker Compose Addition

**Decision**: Add `rooms-service` to `docker-compose.yml` following the same pattern
as `entry-auth-service`:
- No published ports (internal network only)
- `depends_on: postgres` with health condition
- Health check via `GET /actuator/health`
- Environment variables: `SPRING_DATASOURCE_*`, `SESSION_COOKIE_SECURE`,
  `SESSION_TIMEOUT_SECONDS` (read-only; managed by entry-auth-service)

Update `api-gateway` `depends_on` to also include `rooms-service`.
Add `ROOMS_SERVICE_URL: http://rooms-service:8080` to gateway env vars.
