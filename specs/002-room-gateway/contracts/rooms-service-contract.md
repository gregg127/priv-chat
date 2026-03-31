# API Contract: rooms-service

**Feature**: 002-room-gateway
**Service**: `rooms-service`
**Base path** (via API gateway): `/rooms`
**Date**: 2026-03-31

All requests and responses use `Content-Type: application/json`.
All endpoints require `Authorization: Bearer <jwt>` where `<jwt>` is issued
by `entry-auth-service` on login or refresh. `rooms-service` fetches the RSA public key
once at startup via `GET /auth/jwks` (JWKS endpoint) and validates tokens locally — no
per-request inter-service HTTP calls. Private key never leaves `entry-auth-service`.
`rooms-service` is not directly exposed; all traffic routes through the API gateway.

---

## Authentication

Every request must include:
```
Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
```

JWT is issued by `entry-auth-service POST /auth/join` (`token` field in response)
and can be refreshed via `entry-auth-service GET /auth/refresh-token`.

JWT expiry: 15 minutes (configurable via `JWT_EXPIRY_SECONDS`).

**Unauthenticated / invalid JWT response** (applies to all endpoints):
```
HTTP/1.1 401 Unauthorized
{ "error": "Authentication required" }
```

Causes: missing header, malformed JWT, invalid signature, expired token.

---

## GET /rooms

List all public rooms ordered by creation time (newest first).

### Request
No body. Requires `Authorization: Bearer <jwt>`.

### Responses

#### 200 OK
```json
[
  {
    "id": 3,
    "name": "bob-room-1",
    "creatorUsername": "bob",
    "createdAt": "2026-03-31T20:00:00Z",
    "activeMemberCount": 0
  },
  {
    "id": 1,
    "name": "alice-room-1",
    "creatorUsername": "alice",
    "createdAt": "2026-03-31T19:00:00Z",
    "activeMemberCount": 0
  }
]
```
Returns `[]` when no rooms exist. No pagination in v1.

**Note**: `activeMemberCount` is always `0` until the chat feature updates it.

---

## POST /rooms

Create a new public room. Name auto-generated as `{username}-room-{n}` (where
`n` = user's next sequential number). A custom name may optionally be provided.

### Request
```json
{}
```
Or with an optional custom name:
```json
{ "name": "my-custom-room" }
```
- `name`: optional; max 100 characters; must be globally unique
- If omitted or `null`, default naming pattern is used

### Responses

#### 201 Created
```json
{
  "id": 5,
  "name": "alice-room-3",
  "creatorUsername": "alice",
  "createdAt": "2026-03-31T20:05:00Z",
  "activeMemberCount": 0
}
```
**Side effects**: room inserted; `user_room_stats` counters incremented; `CREATE_ROOM` audit log entry.

#### 422 Unprocessable Entity — cap reached
```json
{ "error": "Room limit reached — you cannot create more than 10 rooms" }
```

#### 409 Conflict — custom name already taken
```json
{ "error": "Room name already taken" }
```

#### 400 Bad Request — validation failure
```json
{ "error": "Room name must not exceed 100 characters" }
```

---

## GET /rooms/{id}

Get a single room by ID.

### Request
No body. Requires `Authorization: Bearer <jwt>`.

### Responses

#### 200 OK
```json
{
  "id": 1,
  "name": "alice-room-1",
  "creatorUsername": "alice",
  "createdAt": "2026-03-31T19:00:00Z",
  "activeMemberCount": 0
}
```

#### 404 Not Found
```json
{ "error": "Room not found" }
```

---

## PUT /rooms/{id}

Update a room's name. Creator only (JWT `sub` must match `rooms.creator_username`).

### Request
```json
{ "name": "alice-renamed" }
```
- `name`: required, non-empty after trimming, max 100 characters, globally unique

### Responses

#### 200 OK
```json
{
  "id": 1,
  "name": "alice-renamed",
  "creatorUsername": "alice",
  "createdAt": "2026-03-31T19:00:00Z",
  "activeMemberCount": 0
}
```
**Side effects**: `rooms.name` updated; `UPDATE_ROOM` audit log entry.

#### 403 Forbidden — not the creator
```json
{ "error": "Only the room creator can update this room" }
```
**Side effects**: `UNAUTHORIZED_ATTEMPT` audit log entry.

#### 404 Not Found
```json
{ "error": "Room not found" }
```

#### 409 Conflict
```json
{ "error": "Room name already taken" }
```

#### 400 Bad Request
```json
{ "error": "Room name is required" }
```

---

## DELETE /rooms/{id}

Delete a room. Creator only (JWT `sub` must match `rooms.creator_username`).

### Request
No body. Requires `Authorization: Bearer <jwt>`.

### Responses

#### 204 No Content
*(empty body)*

**Side effects**: room deleted; `user_room_stats.active_rooms_count` decremented (slot freed); `DELETE_ROOM` audit log entry (with room name snapshot).

#### 403 Forbidden — not the creator
```json
{ "error": "Only the room creator can delete this room" }
```
**Side effects**: `UNAUTHORIZED_ATTEMPT` audit log entry.

#### 404 Not Found
```json
{ "error": "Room not found" }
```

---

## Error Response Format

All error responses: `{ "error": "Human-readable message" }`

| Code | Meaning |
|------|---------|
| 200 | OK |
| 201 | Created |
| 204 | No Content |
| 400 | Validation failure |
| 401 | Missing / invalid / expired JWT |
| 403 | Valid JWT but not permitted (non-creator mutation) |
| 404 | Room not found |
| 409 | Name uniqueness conflict |
| 422 | Business rule violation (room cap) |
| 500 | Internal Server Error |

---

## entry-auth-service Contract Changes

`entry-auth-service` is updated to issue JWTs. The following additions apply:

### Updated: POST /auth/join — 200 OK response

```json
{
  "username": "alice",
  "token": "eyJhbGciOiJSUzI1NiJ9..."
}
```
(`token` field is new; JWT expires in `JWT_EXPIRY_SECONDS` seconds, default 900)

### New: GET /auth/jwks

Returns the RSA public key in JWK Set format. Unauthenticated.

**200 OK**:
```json
{
  "keys": [{
    "kty": "RSA",
    "use": "sig",
    "alg": "RS256",
    "kid": "priv-chat-1",
    "n": "<base64url modulus>",
    "e": "AQAB"
  }]
}
```
Used by `rooms-service` (and any future service) at startup to obtain the public key
for local JWT signature verification. No credentials required.

### New: GET /auth/refresh-token

Validates the current session cookie and issues a fresh JWT.

**Request**: No body. Requires valid `SESSION` cookie.

**200 OK**:
```json
{ "token": "eyJhbGciOiJIUzI1NiJ9..." }
```

**401 Unauthorized** (no valid session):
```json
{ "error": "Authentication required" }
```

---

## Security Notes

- `creatorUsername` in responses is informational; authorization uses JWT `sub` only
- All mutation endpoints (POST, PUT, DELETE) write to `room_audit_log`
- `rooms-service` creates no server-side sessions (`SessionCreationPolicy.STATELESS`)
- `JWT_PRIVATE_KEY` (base64 PEM) is held exclusively by `entry-auth-service` — never shared
- `rooms-service` has no published Docker port — accessible only via `privchat-net`

---

## Gateway Routing

`application.yml` in `api-gateway`:
```yaml
services:
  auth:
    url: ${ENTRY_AUTH_SERVICE_URL:http://entry-auth-service:8080}
  rooms:
    url: ${ROOMS_SERVICE_URL:http://rooms-service:8080}
```

`RoomsProxyController` maps `@RequestMapping("/rooms/**")` and forwards to
`services.rooms.url` — mirrors `AuthProxyController` exactly.
