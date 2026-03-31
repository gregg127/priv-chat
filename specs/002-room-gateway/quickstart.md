# Developer Quickstart: Room Gateway

**Feature**: 002-room-gateway
**Date**: 2026-03-31

---

## Prerequisites

- Java 25 (verify: `java --version`)
- Docker + Docker Compose (verify: `docker compose version`)
- Existing `implementation/` directory with `entry-auth-service` built successfully

---

## 1. Start the full stack

```bash
cd implementation
docker compose up --build
```

This starts: PostgreSQL → entry-auth-service → rooms-service → api-gateway → frontend

**Health check URLs** (from host):
- API Gateway: `http://localhost:8080/actuator/health`
- Frontend: `http://localhost:3000`

---

## 2. Authenticate first (entry-auth-service)

All rooms endpoints require a valid session. Join the network first:

```bash
curl -c cookies.txt -X POST http://localhost:8080/auth/join \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "changeme"}'
# Response: {"username": "alice"}
```

The session cookie is saved to `cookies.txt`.

---

## 3. Room CRUD operations

**List all rooms**:
```bash
curl -b cookies.txt http://localhost:8080/rooms
# Response: [] (empty on fresh start)
```

**Create a room** (auto-named `alice-room-1`):
```bash
curl -b cookies.txt -X POST http://localhost:8080/rooms \
  -H "Content-Type: application/json"
# Response: {"id":1,"name":"alice-room-1","creatorUsername":"alice",...}
```

**Create a room with custom name**:
```bash
curl -b cookies.txt -X POST http://localhost:8080/rooms \
  -H "Content-Type: application/json" \
  -d '{"name": "my-custom-room"}'
```

**Get a specific room**:
```bash
curl -b cookies.txt http://localhost:8080/rooms/1
```

**Update a room name** (creator only):
```bash
curl -b cookies.txt -X PUT http://localhost:8080/rooms/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "alice-renamed"}'
```

**Delete a room** (creator only):
```bash
curl -b cookies.txt -X DELETE http://localhost:8080/rooms/1
# Response: 204 No Content
```

---

## 4. Run rooms-service tests

```bash
cd implementation/services/rooms-service
./gradlew test
```

Tests require Docker (Testcontainers spins up a PostgreSQL container automatically).

---

## 5. Rebuild after code changes

```bash
# Rebuild only rooms-service and restart
docker compose up --build rooms-service

# Rebuild gateway (after adding RoomsProxyController)
docker compose up --build api-gateway
```

---

## 6. Environment variables reference

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/privchat` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `privchat` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `changeme` | DB password |
| `SESSION_COOKIE_SECURE` | `false` | Set `true` in production (HTTPS) |
| `SESSION_TIMEOUT_SECONDS` | `86400` | Session timeout in seconds (read-only; managed by entry-auth-service) |
| `ROOMS_SERVICE_URL` | `http://rooms-service:8080` | Gateway → rooms-service URL |

---

## 7. Key files

| File | Purpose |
|------|---------|
| `services/rooms-service/src/main/resources/db/migration/V1__create_rooms.sql` | Rooms table DDL |
| `services/rooms-service/src/main/resources/db/migration/V2__create_user_room_stats.sql` | Per-user stats table |
| `services/rooms-service/src/main/resources/db/migration/V3__create_room_audit_log.sql` | Audit log table |
| `services/rooms-service/src/main/java/com/privchat/rooms/config/SecurityConfig.java` | Spring Security: all routes authenticated |
| `services/rooms-service/src/main/java/com/privchat/rooms/config/SessionConfig.java` | Spring Session JDBC (shared store) |
| `services/rooms-service/src/main/java/com/privchat/rooms/service/RoomService.java` | Cap enforcement, naming logic, transactions |
| `api-gateway/src/main/java/com/privchat/gateway/proxy/RoomsProxyController.java` | Gateway proxy for /rooms/** |
