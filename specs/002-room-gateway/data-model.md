# Data Model: Room Gateway

**Feature**: 002-room-gateway
**Branch**: `002-room-gateway`
**Date**: 2026-03-31

**Database**: `rooms` (dedicated PostgreSQL 17 container: `postgres-rooms`)
`rooms-service` owns all tables below. It has no access to the `privchat`
database used by `entry-auth-service`.

---

## Entities

### Room

A named public conversation space. Permanent once created. Managed by `rooms-service`.

**Table**: `rooms`
**Flyway migration**: `V1__create_rooms.sql`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGSERIAL` | PK | Auto-incrementing room ID |
| `name` | `VARCHAR(100)` | UNIQUE NOT NULL | Display name (e.g., `alice-room-1`) |
| `creator_username` | `VARCHAR(64)` | NOT NULL | Username from JWT `sub` claim at creation time; immutable |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `NOW()` | UTC creation timestamp |
| `active_member_count` | `INT` | NOT NULL DEFAULT 0 CHECK (>= 0) | Denormalized occupant count — reserved for future chat feature; always 0 in this feature |

**Indexes**:
- `(created_at DESC)` — ordered list retrieval
- `(creator_username)` — per-user queries (cap check, creator validation)
- `UNIQUE(name)` — DB-level uniqueness enforcement

**Constraints**:
- `creator_username` is immutable after insertion (never updated)
- `active_member_count` is never modified by rooms-service (reserved for chat feature)

**Lifecycle**:
- Created: `POST /rooms` (JWT authenticated; cap checked; name resolved)
- Read: `GET /rooms`, `GET /rooms/{id}` (all authenticated users)
- Updated: `PUT /rooms/{id}` — `name` only; creator (JWT `sub`) only
- Deleted: `DELETE /rooms/{id}` — creator only; decrements `user_room_stats.active_rooms_count`

**Java representation**: `com.privchat.rooms.model.Room` (Java record)

---

### UserRoomStats

Per-user creation statistics. Enforces the 10-room active cap and drives the
sequential default room naming (`{username}-room-{n}`).

**Table**: `user_room_stats`
**Flyway migration**: `V2__create_user_room_stats.sql`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `username` | `VARCHAR(64)` | PK | Portal username (from JWT `sub` claim) |
| `rooms_created_count` | `INT` | NOT NULL DEFAULT 0 CHECK (>= 0) | Monotonically increasing naming counter; never decremented |
| `active_rooms_count` | `INT` | NOT NULL DEFAULT 0 CHECK (>= 0 AND <= 10) | Active (non-deleted) rooms; enforces the 10-room cap |

**Key invariant**: `active_rooms_count <= rooms_created_count` always.

**Operations** (all within the same transaction as room mutation):
- On `POST /rooms`: upsert row, `rooms_created_count + 1`, `active_rooms_count + 1`
- On `DELETE /rooms/{id}`: `active_rooms_count - 1` (slot freed; name counter unchanged)

**Java representation**: `com.privchat.rooms.model.UserRoomStats` (Java record)

---

### RoomAuditLog

Append-only security event log. Required by the constitution (audit trail).
Mirrors the pattern of `security_audit_log` in `entry-auth-service`.

**Table**: `room_audit_log`
**Flyway migration**: `V3__create_room_audit_log.sql`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGSERIAL` | PK | Auto-incrementing entry ID |
| `event_type` | `VARCHAR(50)` | NOT NULL CHECK | `CREATE_ROOM`, `UPDATE_ROOM`, `DELETE_ROOM`, `UNAUTHORIZED_ATTEMPT` |
| `room_id` | `BIGINT` | NULLABLE | Affected room ID (NULL when room not found) |
| `room_name` | `VARCHAR(100)` | NULLABLE | Room name snapshot at event time (preserves history after deletion) |
| `actor_username` | `VARCHAR(64)` | NOT NULL | Username from JWT `sub` claim |
| `occurred_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `NOW()` | UTC event timestamp |

**Constraints**: Append-only — no UPDATE or DELETE from application code.

**Indexes**:
- `(actor_username, occurred_at DESC)` — per-user activity
- `(event_type, occurred_at DESC)` — operational monitoring

**Java representation**: `com.privchat.rooms.model.RoomAuditLog` (Java record)

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

`rooms-service` Flyway targets `postgres-rooms` exclusively. No migrations touch
the `privchat` database belonging to `entry-auth-service`.

---

## No Shared Session Store

`rooms-service` does **not** connect to the session tables managed by
`entry-auth-service`. Authentication is entirely JWT-based and stateless:
the JWT `sub` claim carries the username for every request.
