# Developer Quickstart: Room Gateway

**Feature**: 002-room-gateway
**Date**: 2026-03-31

---

## Prerequisites

- Java 25 (`java --version`)
- Docker + Docker Compose (`docker compose version`)
- `JWT_SECRET` env var set (≥ 32 characters) — must be the same for both services

---

## 1. Configure environment

Create or update `.env` in `implementation/`:
```bash
# Existing auth DB
POSTGRES_DB=privchat
POSTGRES_USER=privchat
POSTGRES_PASSWORD=changeme
NETWORK_PASSWORD=changeme

# Rooms DB (new — separate container)
ROOMS_DB=rooms
ROOMS_DB_USER=rooms
ROOMS_DB_PASSWORD=changeme-rooms

# JWT shared secret (≥ 32 chars — CHANGE THIS IN PRODUCTION)
JWT_SECRET=this-is-a-dev-secret-must-be-32chars-min
JWT_EXPIRY_SECONDS=900
```

---

## 2. Start the full stack

```bash
cd implementation
docker compose up --build
```

Startup order: `postgres` → `postgres-rooms` → `entry-auth-service` → `rooms-service` → `api-gateway` → `frontend`

**Health checks**:
- `curl http://localhost:8080/actuator/health`        # gateway
- `curl http://localhost:3000`                         # frontend

---

## 3. Authenticate and get a JWT

```bash
# Join the network — response now includes a JWT token
curl -c cookies.txt -X POST http://localhost:8080/auth/join \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "changeme"}'

# Response:
# {"username":"alice","token":"eyJhbGciOiJIUzI1NiJ9..."}

# Save the token
TOKEN="eyJhbGciOiJIUzI1NiJ9..."
```

---

## 4. Room CRUD (all require Authorization header)

```bash
# List all rooms
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/rooms
# → [] on fresh start

# Create a room (auto-named alice-room-1)
curl -X POST http://localhost:8080/rooms \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
# → {"id":1,"name":"alice-room-1","creatorUsername":"alice",...}

# Create with custom name
curl -X POST http://localhost:8080/rooms \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"my-room"}'

# Get a specific room
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/rooms/1

# Update room name (creator only)
curl -X PUT http://localhost:8080/rooms/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"alice-renamed"}'

# Delete a room (creator only)
curl -X DELETE -H "Authorization: Bearer $TOKEN" http://localhost:8080/rooms/1
# → 204 No Content
```

---

## 5. Refresh the JWT (before it expires in 15 min)

```bash
# Use the session cookie to get a new JWT
curl -b cookies.txt http://localhost:8080/auth/refresh-token
# → {"token":"eyJhbGciOiJIUzI1NiJ9..."}
```

---

## 6. Run rooms-service tests

```bash
cd implementation/services/rooms-service
./gradlew test
# Testcontainers spins up postgres-rooms automatically
```

---

## 7. Environment variables reference

| Variable | Default | Service | Description |
|----------|---------|---------|-------------|
| `JWT_SECRET` | *(required)* | both | HS256 signing secret — min 32 bytes |
| `JWT_EXPIRY_SECONDS` | `900` | both | JWT lifetime in seconds (default 15 min) |
| `ROOMS_DB` | `rooms` | rooms-service | PostgreSQL DB name |
| `ROOMS_DB_USER` | `rooms` | rooms-service | PostgreSQL username |
| `ROOMS_DB_PASSWORD` | `changeme-rooms` | rooms-service | PostgreSQL password |
| `ROOMS_SERVICE_URL` | `http://rooms-service:8080` | api-gateway | Internal URL for rooms proxy |
| `SESSION_COOKIE_SECURE` | `false` | entry-auth-service | Set `true` in production |

---

## 8. Key files

| File | Purpose |
|------|---------|
| `services/entry-auth-service/…/service/JwtService.java` | JWT generation (JJWT 0.12.6) |
| `services/entry-auth-service/…/controller/AuthController.java` | Updated: `JoinResponse` includes `token` field; new `GET /auth/refresh-token` |
| `services/rooms-service/…/config/SecurityConfig.java` | Stateless JWT filter chain |
| `services/rooms-service/…/config/JwtAuthFilter.java` | OncePerRequestFilter: validates JWT, injects principal |
| `services/rooms-service/…/service/JwtService.java` | JWT validation only (no issuance) |
| `services/rooms-service/…/service/RoomService.java` | Cap enforcement, naming, transactions |
| `services/rooms-service/src/main/resources/db/migration/` | V1–V3 Flyway migrations |
| `api-gateway/…/proxy/RoomsProxyController.java` | `/rooms/**` proxy to rooms-service |
| `docker-compose.yml` | Adds `postgres-rooms` + `rooms-service` services |
