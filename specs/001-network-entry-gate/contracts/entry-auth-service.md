# API Contract: entry-auth-service

**Feature**: 001-network-entry-gate
**Service**: `entry-auth-service`
**Base path** (via API gateway): `/auth`
**Date**: 2026-03-26

All requests and responses use `Content-Type: application/json`.
All endpoints are accessible through the API gateway; the entry-auth-service is not
exposed directly outside the Docker network.

---

## POST /auth/join

Join the portal network. Validates the shared network password and creates a
server-side session.

### Request

```json
{
  "username": "string (1–64 chars, trimmed)",
  "password": "string (non-empty)"
}
```

**Validation rules**:
- `username`: required, non-empty after trimming, max 64 characters
- `password`: required, non-empty

### Responses

#### 200 OK — Successful join

Sets `Set-Cookie: SESSION=<session-id>; HttpOnly; Secure; SameSite=Strict; Path=/`

```json
{
  "username": "alice"
}
```

**Side effects**:
- Server-side session created with `username` and `authenticated=true`
- `JOIN_SUCCESS` event written to `security_audit_log`

#### 401 Unauthorized — Wrong password or empty credentials

```json
{
  "error": "Incorrect network password"
}
```

**Note**: The error message does NOT distinguish between wrong password and
wrong username — intentional (FR-003).

**Side effects**:
- `JOIN_FAILURE` event written to `security_audit_log`
- Rate-limit bucket for the client IP decremented by 1 token

#### 400 Bad Request — Validation failure

```json
{
  "error": "Username is required"
}
```

Possible error values: `"Username is required"`, `"Password is required"`,
`"Username must not exceed 64 characters"`

**Side effects**: None (no logging, no rate-limit decrement)

#### 429 Too Many Requests — Rate limited

```json
{
  "error": "Too many attempts — please try again in {minutes} minutes"
}
```

Response header: `Retry-After: <seconds>`

**Side effects**:
- `RATE_LIMITED` event written to `security_audit_log`

---

## DELETE /auth/session

Explicitly end the current session (logout).

### Request

No body. Requires valid `SESSION` cookie.

### Responses

#### 200 OK — Session invalidated

```json
{
  "message": "Signed out"
}
```

**Side effects**:
- Server-side session destroyed
- `Set-Cookie: SESSION=; Max-Age=0; HttpOnly; Secure; SameSite=Strict; Path=/`
  (cookie cleared on client)

#### 401 Unauthorized — No active session

```json
{
  "error": "No active session"
}
```

---

## GET /auth/session

Check whether the current session is valid. Used by the frontend to determine
whether to show the entry gate or the portal interior.

### Request

No body. Requires valid `SESSION` cookie.

### Responses

#### 200 OK — Session valid

```json
{
  "username": "alice",
  "authenticated": true
}
```

#### 401 Unauthorized — No valid session

```json
{
  "authenticated": false
}
```

---

## Error Response Format

All error responses follow the same shape:

```json
{
  "error": "Human-readable error message"
}
```

---

## Security Notes

- The `SESSION` cookie is **HttpOnly** (not accessible via JavaScript),
  **Secure** (HTTPS only), and **SameSite=Strict** (CSRF protection).
- The shared network password is **never** returned in any response.
- The shared network password is **never** logged.
- IP addresses in the rate-limit system are sourced from the
  `X-Forwarded-For` header set by the API gateway (trusted proxy only).

---

## Gateway Routing Configuration

The API gateway (`AuthProxyController`) forwards all requests matching `/auth/**` to
`entry-auth-service:8080` using Spring's `RestClient`. All request headers are forwarded;
`X-Forwarded-For`, `X-Forwarded-Proto`, and `X-Forwarded-Host` are added automatically.

The entry-auth-service is not exposed outside the Docker network; all traffic must pass
through the gateway on port 8080.
