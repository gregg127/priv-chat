# API Contract: rooms-service

**Feature**: 002-room-gateway
**Service**: `rooms-service`
**Base path** (via API gateway): `/rooms`
**Date**: 2026-03-31

All requests and responses use `Content-Type: application/json`.
All endpoints require a valid `SESSION` cookie (established by `entry-auth-service`).
All endpoints return `401 Unauthorized` when no valid session is present.
`rooms-service` is not directly exposed; all traffic routes through the API gateway.

---

## Authentication

Every request must carry the `SESSION` cookie. `rooms-service` resolves it via the
shared Spring Session JDBC store. No login endpoint exists on this service.

**Unauthenticated response** (applies to all endpoints below):

```json
HTTP/1.1 401 Unauthorized
{
  "error": "Authentication required"
}
```

---

## GET /rooms

List all public rooms, ordered by creation time (newest first).

### Request

No body. Requires valid `SESSION` cookie.

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

Returns an empty array `[]` when no rooms exist (empty-state case).

**Notes**:
- `activeMemberCount` is always `0` until the chat/messaging feature updates it
- No pagination in v1; all rooms returned in a single response

---

## POST /rooms

Create a new public room. The room name is automatically generated as
`{username}-room-{n}` where `n` is the user's next sequential room number.
The user is capped at 10 active rooms.

### Request

No body required. The room name is derived server-side from the authenticated username
and the user's `rooms_created_count + 1` from `user_room_stats`.

Optionally, a custom name may be provided (CRUD extension):

```json
{
  "name": "my-custom-room"
}
```

- If `name` is omitted or null, the default naming pattern is used
- `name` max length: 100 characters
- `name` must be unique across all rooms

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

**Side effects**:
- Room inserted into `rooms` table
- `user_room_stats.rooms_created_count` and `active_rooms_count` incremented
- `CREATE_ROOM` event written to `room_audit_log`

#### 409 Conflict — Room name already taken (custom name only)

```json
{
  "error": "Room name already taken"
}
```

#### 422 Unprocessable Entity — Room creation limit reached

```json
{
  "error": "Room limit reached — you cannot create more than 10 rooms"
}
```

**Side effects**: None (no audit log entry; no state change)

#### 400 Bad Request — Validation failure

```json
{
  "error": "Room name must not exceed 100 characters"
}
```

---

## GET /rooms/{id}

Retrieve a single room by its ID.

### Request

No body. Requires valid `SESSION` cookie.

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
{
  "error": "Room not found"
}
```

---

## PUT /rooms/{id}

Update a room's name. Only the room creator may update the room.

### Request

```json
{
  "name": "alice-new-name"
}
```

**Validation rules**:
- `name`: required, non-empty after trimming, max 100 characters, must be globally unique

### Responses

#### 200 OK

```json
{
  "id": 1,
  "name": "alice-new-name",
  "creatorUsername": "alice",
  "createdAt": "2026-03-31T19:00:00Z",
  "activeMemberCount": 0
}
```

**Side effects**:
- `rooms.name` updated
- `UPDATE_ROOM` event written to `room_audit_log` (includes old name in a `details` field)

#### 403 Forbidden — Authenticated user is not the room creator

```json
{
  "error": "Only the room creator can update this room"
}
```

**Side effects**:
- `UNAUTHORIZED_ATTEMPT` event written to `room_audit_log`

#### 404 Not Found

```json
{
  "error": "Room not found"
}
```

#### 409 Conflict — Name already taken

```json
{
  "error": "Room name already taken"
}
```

#### 400 Bad Request

```json
{
  "error": "Room name is required"
}
```

---

## DELETE /rooms/{id}

Delete a room. Only the room creator may delete the room.

### Request

No body. Requires valid `SESSION` cookie.

### Responses

#### 204 No Content

*(empty body)*

**Side effects**:
- Room deleted from `rooms` table
- `user_room_stats.active_rooms_count` decremented (frees up one slot for future creation)
- `DELETE_ROOM` event written to `room_audit_log` (includes room name snapshot)

#### 403 Forbidden — Authenticated user is not the room creator

```json
{
  "error": "Only the room creator can delete this room"
}
```

**Side effects**:
- `UNAUTHORIZED_ATTEMPT` event written to `room_audit_log`

#### 404 Not Found

```json
{
  "error": "Room not found"
}
```

---

## Error Response Format

All error responses follow the same structure:

```json
{
  "error": "Human-readable error message"
}
```

HTTP status codes used:

| Code | Meaning |
|------|---------|
| 200 | OK |
| 201 | Created |
| 204 | No Content |
| 400 | Bad Request (validation failure) |
| 401 | Unauthorized (no valid session) |
| 403 | Forbidden (valid session but not permitted) |
| 404 | Not Found |
| 409 | Conflict (name uniqueness violation) |
| 422 | Unprocessable Entity (business rule violation — room cap) |
| 500 | Internal Server Error |

---

## Security Notes

- Creator identity is read from the **authenticated session** (`PRINCIPAL_NAME`),
  never from the request body or URL parameters
- The `creatorUsername` field in responses is informational only and is not used
  for authorization decisions
- All mutation operations (POST, PUT, DELETE) are logged to `room_audit_log`
- `rooms-service` has no published port; only accessible via gateway on `privchat-net`

---

## Gateway Routing Configuration

Add to `application.yml` in `api-gateway`:

```yaml
services:
  auth:
    url: ${ENTRY_AUTH_SERVICE_URL:http://entry-auth-service:8080}
  rooms:
    url: ${ROOMS_SERVICE_URL:http://rooms-service:8080}
```

Add `RoomsProxyController` mapping `@RequestMapping("/rooms/**")` to `rooms-service`
(mirrors `AuthProxyController` with `services.rooms.url` as the base URL).
