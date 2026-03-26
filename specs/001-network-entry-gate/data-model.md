# Data Model: Network Entry Gate

**Feature**: 001-network-entry-gate
**Branch**: `001-network-entry-gate`
**Date**: 2026-03-26

## Entities

### Session

Represents an authenticated presence in the portal. Managed by Spring Session
JDBC, stored in PostgreSQL. The session ID is opaque and delivered to the
client as an HttpOnly, Secure, SameSite=Strict cookie.

**Table**: `spring_session` (managed by Spring Session JDBC auto-schema)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `PRIMARY_ID` | `CHAR(36)` | PK | Internal Spring Session UUID |
| `SESSION_ID` | `CHAR(36)` | UNIQUE NOT NULL | Opaque session ID sent to client |
| `CREATION_TIME` | `BIGINT` | NOT NULL | Epoch millis of session creation |
| `LAST_ACCESS_TIME` | `BIGINT` | NOT NULL | Epoch millis of last access |
| `MAX_INACTIVE_INTERVAL` | `INT` | NOT NULL | Seconds before session expires on inactivity |
| `EXPIRY_TIME` | `BIGINT` | NOT NULL | Computed expiry epoch millis (indexed) |
| `PRINCIPAL_NAME` | `VARCHAR(100)` | NULLABLE | Display name chosen by the user |

**Table**: `spring_session_attributes` (Spring Session JDBC attribute store)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `SESSION_PRIMARY_ID` | `CHAR(36)` | FK → `spring_session.PRIMARY_ID` | Owning session |
| `ATTRIBUTE_NAME` | `VARCHAR(200)` | NOT NULL | Attribute key |
| `ATTRIBUTE_BYTES` | `BYTEA` | NOT NULL | Serialised attribute value |

**Key session attributes stored**:
- `username` — the display name chosen at join time (String)
- `authenticated` — boolean flag set to `true` on successful join

**Lifecycle**:
- Created: on successful `POST /auth/join`
- Extended: on every authenticated request (rolling expiry)
- Destroyed: on `DELETE /auth/session` (explicit logout) or server-side expiry

**Expiry**: Configurable via `server.servlet.session.timeout` (default: 24 hours
of inactivity). Spring Session JDBC cleanup task removes expired rows.

---

### SecurityAuditLog

An append-only server-side log of security-relevant events on the entry gate.
Required by constitution (Security Requirements — Audit trail).

**Table**: `security_audit_log`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGSERIAL` | PK | Auto-incrementing log entry ID |
| `event_type` | `VARCHAR(50)` | NOT NULL | One of: `JOIN_SUCCESS`, `JOIN_FAILURE`, `RATE_LIMITED` |
| `ip_address` | `VARCHAR(45)` | NOT NULL | Client IP (IPv4 or IPv6, max 45 chars) |
| `username` | `VARCHAR(64)` | NULLABLE | Display name (populated on `JOIN_SUCCESS` only; NULL otherwise) |
| `occurred_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT `NOW()` | UTC timestamp of the event |

**Constraints**:
- `event_type` MUST be one of the three defined values (enforced via CHECK constraint)
- `username` MUST be NULL for `JOIN_FAILURE` and `RATE_LIMITED` events (no username exposed in failure log)
- Table is append-only; no UPDATE or DELETE operations permitted from application code

**Indexes**:
- `(ip_address, occurred_at DESC)` — for rate-limit window queries
- `(event_type, occurred_at DESC)` — for operational monitoring queries

**Flyway migration**: `V1__create_security_audit_log.sql`

---

### RateLimitBucket (in-memory, Bucket4j)

Not persisted to the database in v1. Implemented as a Caffeine-backed in-memory
cache in the entry-auth-service JVM.

| Field | Type | Description |
|-------|------|-------------|
| `ip_address` | String (key) | Client IP address |
| `bucket` | Bucket4j `Bucket` | Token bucket: capacity 5, refill 5 tokens per 10 minutes |
| `ttl` | 10 minutes | Cache entry expires after 10 minutes of inactivity |

**Note**: In-memory storage means rate limit state is lost on entry-auth-service
restart. Acceptable for v1; future upgrade path is Bucket4j + PostgreSQL or
Redis-backed bucket store.

---

## Entity Relationships

```
spring_session ──< spring_session_attributes
                (one session has many attributes)

security_audit_log
  (independent append-only log; no FK to session)

RateLimitBucket
  (in-memory cache; no DB persistence in v1)
```

## Database Migration Strategy

- **Tool**: Flyway (managed within entry-auth-service)
- **Location**: `implementation/services/entry-auth-service/src/main/resources/db/migration/`
- **Migrations**:
  - `V1__create_security_audit_log.sql` — creates `security_audit_log` table + indexes
  - Spring Session JDBC schema auto-created by Spring Session on first startup
    (or via `schema.sql` if explicit control is needed)
- **Naming convention**: `V{n}__{description}.sql` (sequential integer versions)
