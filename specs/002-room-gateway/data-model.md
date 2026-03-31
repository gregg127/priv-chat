# Data Model: Room Gateway

**Feature**: 002-room-gateway
**Branch**: `002-room-gateway`
**Date**: 2026-03-31

---

## Entities

### Room

Represents a named public conversation space. Created by an authenticated user;
permanent once created (never auto-removed). Managed by `rooms-service`.

**Table**: `rooms`
**Flyway migration**: `V1__create_rooms.sql`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGSERIAL` | PK | Auto-incrementing room ID |
| `name` | `VARCHAR(100)` | UNIQUE NOT NULL | Room display name (e.g., `alice-room-1`) |
| `creator_username` | `VARCHAR(64)` | NOT NULL | Username of the creator (from session at creation time) |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `NOW()` | UTC timestamp of room creation |
| `active_member_count` | `INT` | NOT NULL DEFAULT 0 CHECK (>= 0) | Current occupant count — denormalized, updated by future chat feature |

**Indexes**:
- `(created_at DESC)` — for ordered list retrieval
- `(creator_username)` — for per-user room queries (cap check, creator validation)
- `UNIQUE(name)` — enforced at DB level in addition to application-level collision check

**Constraints**:
- `name` must be 1–100 characters after trimming
- `creator_username` is immutable after creation (no UPDATE permitted on this column)
- `active_member_count` is never updated by `rooms-service` (reserved for chat feature)

**Lifecycle**:
- Created: `POST /rooms` (authenticated; cap checked; naming sequence resolved)
- Read: `GET /rooms`, `GET /rooms/{id}` (all authenticated users)
- Updated: `PUT /rooms/{id}` — only `name` field is mutable; creator only
- Deleted: `DELETE /rooms/{id}` — creator only; decrements `user_room_stats.active_rooms_count`

**Java representation**: `com.privchat.rooms.model.Room` (plain Java record)

---

### UserRoomStats

Tracks per-user room creation statistics. Used to enforce the 10-room active cap
and to determine the next sequential default room name.

**Table**: `user_room_stats`
**Flyway migration**: `V2__create_user_room_stats.sql`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `username` | `VARCHAR(64)` | PK | The portal username (matches `PRINCIPAL_NAME` in session store) |
| `rooms_created_count` | `INT` | NOT NULL DEFAULT 0 CHECK (>= 0) | Monotonically increasing counter — used to compute the next room name suffix; never decremented |
| `active_rooms_count` | `INT` | NOT NULL DEFAULT 0 CHECK (>= 0 AND <= 10) | Count of currently active (non-deleted) rooms; enforces the 10-room cap |

**Key invariant**: `active_rooms_count <= rooms_created_count` at all times.

**Operations**:
- Row upserted (`INSERT … ON CONFLICT DO NOTHING`) on first room creation
- On `POST /rooms`: `rooms_created_count + 1`, `active_rooms_count + 1` (in same transaction as room insert)
- On `DELETE /rooms/{id}`: `active_rooms_count - 1`
- `rooms_created_count` is never decremented (ensures name uniqueness over time)

**Java representation**: `com.privchat.rooms.model.UserRoomStats` (plain Java record)

---

### RoomAuditLog

Append-only record of security-relevant room mutations. Required by the constitution
(Security Requirements — audit trail). Mirrors the pattern of `security_audit_log`
in `entry-auth-service`.

**Table**: `room_audit_log`
**Flyway migration**: `V3__create_room_audit_log.sql`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGSERIAL` | PK | Auto-incrementing log entry ID |
| `event_type` | `VARCHAR(50)` | NOT NULL | One of: `CREATE_ROOM`, `UPDATE_ROOM`, `DELETE_ROOM`, `UNAUTHORIZED_ATTEMPT` |
| `room_id` | `BIGINT` | NULLABLE | The affected room ID (NULL for `UNAUTHORIZED_ATTEMPT` when room not found) |
| `room_name` | `VARCHAR(100)` | NULLABLE | Room name at time of event (snapshot; preserves history after deletion) |
| `actor_username` | `VARCHAR(64)` | NOT NULL | Username performing the action (from session) |
| `occurred_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `NOW()` | UTC timestamp |

**Constraints**:
- `event_type` CHECK constraint: must be one of the four defined values
- Table is append-only — no UPDATE or DELETE permitted from application code

**Indexes**:
- `(actor_username, occurred_at DESC)` — per-user activity queries
- `(event_type, occurred_at DESC)` — operational monitoring

**Java representation**: `com.privchat.rooms.model.RoomAuditLog` (plain Java record)

---

## Shared Session Store (read-only reference)

`rooms-service` reads from the Spring Session JDBC tables owned by `entry-auth-service`
to resolve authenticated sessions. These tables are not managed by `rooms-service`
Flyway migrations.

| Table | Owner | rooms-service access |
|-------|-------|---------------------|
| `SPRING_SESSION` | `entry-auth-service` | Read-only (session lookup + last-access update by Spring Session framework) |
| `SPRING_SESSION_ATTRIBUTES` | `entry-auth-service` | Read-only (principal name extraction) |

The `PRINCIPAL_NAME` column of `SPRING_SESSION` is populated with the portal username
at join time by `entry-auth-service`. Spring Security in `rooms-service` uses this
automatically as the authenticated principal.

---

## Entity Relationships

```
user_room_stats (username PK)
       │ 1
       │ creates
       ↓ N
    rooms (id PK)
       │ 1
       │ logged in
       ↓ N
room_audit_log (id PK)
```

---

## Flyway Migration Order

| Version | File | Creates |
|---------|------|---------|
| V1 | `V1__create_rooms.sql` | `rooms` table + indexes |
| V2 | `V2__create_user_room_stats.sql` | `user_room_stats` table |
| V3 | `V3__create_room_audit_log.sql` | `room_audit_log` table + indexes |

**Note**: `rooms-service` Flyway is configured with a dedicated schema baseline.
It does NOT touch `entry-auth-service` tables. Both services run Flyway independently
against the same PostgreSQL database using schema-prefixed table names to avoid conflicts
(or use the default public schema with clearly separated table names — rooms tables have
no naming overlap with auth tables).
