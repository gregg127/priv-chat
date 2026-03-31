# Research: Room Gateway

**Feature**: 002-room-gateway
**Date**: 2026-03-31
**Phase**: Phase 0 — Technology Research

---

## 1. JWT Authentication Strategy

**Decision**: HS256 JWT signed with a shared secret (`JWT_SECRET` env var ≥ 32 bytes).
`entry-auth-service` issues the JWT on successful login; `rooms-service` validates it
locally using the same secret. No per-request inter-service HTTP calls.

**JWT Claims**:
```json
{
  "sub": "alice",
  "iat": 1743451200,
  "exp": 1743452100
}
```
- `sub`: portal username (used as principal in rooms-service)
- `iat`: issued-at epoch seconds
- `exp`: expiry epoch seconds (`iat + JWT_EXPIRY_SECONDS`, default 900 = 15 minutes)

**Library**: JJWT 0.12.6 (`io.jsonwebtoken`) — the de-facto standard JWT library for
Java. Actively maintained, well-audited, minimal footprint.

```groovy
// Both entry-auth-service and rooms-service build.gradle:
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
```

**Issue flow** (entry-auth-service):
1. `POST /auth/join` succeeds → `JwtService.generateToken(username)` → JWT string
2. JWT returned in `JoinResponse.token` field (new field alongside existing `username`)
3. `GET /auth/refresh-token` (new endpoint) → validates session → returns fresh JWT

**Validation flow** (rooms-service):
1. `JwtAuthFilter extends OncePerRequestFilter` intercepts every request
2. Extracts `Authorization: Bearer <token>` header
3. `JwtService.validateToken(token)` → verifies signature + expiry
4. On success: injects `UsernamePasswordAuthenticationToken(username, ...)` into
   `SecurityContextHolder` — no session created (`SessionCreationPolicy.STATELESS`)
5. On failure: returns `401 Unauthorized`

**Why HS256 over RS256**:
- Shared secret is simpler for a small fixed-topology deployment (two services)
- RS256 requires a key pair and public key distribution mechanism
- YAGNI: if more services need JWT validation, migrate to RS256 at that point

**Revocation tradeoff**:
- JWTs are stateless — cannot be invalidated before expiry
- Mitigated by 15-minute expiry: max exposure window on logout is 15 minutes
- Frontend discards the JWT immediately on logout
- The constitution explicitly accepts this: "token-based with short-lived credentials"

**Alternatives Considered**:
- **OAuth2 introspection (rooms-service calls entry-auth-service per request)**:
  Adds network latency + failure mode on every rooms request. Rejected: complexity
  without benefit in a two-service closed system.
- **Shared Spring Session JDBC**: Violates database isolation requirement (user
  instruction: separate databases). Rejected.
- **API Key**: No expiry, no user identity encoded, not industry-standard for
  user-context auth. Rejected.

---

## 2. Separate PostgreSQL Database

**Decision**: Add a dedicated `postgres-rooms` PostgreSQL 17 container to
`docker-compose.yml`. `rooms-service` connects exclusively to this container.
`entry-auth-service` continues using `postgres` (unchanged).

**Configuration**:
```yaml
# docker-compose.yml addition
postgres-rooms:
  image: postgres:17-alpine
  environment:
    POSTGRES_DB:       ${ROOMS_DB:-rooms}
    POSTGRES_USER:     ${ROOMS_DB_USER:-rooms}
    POSTGRES_PASSWORD: ${ROOMS_DB_PASSWORD:-changeme}
  volumes: [postgres_rooms_data:/var/lib/postgresql/data]
  networks: [privchat-net]
  healthcheck: { ... }

rooms-service:
  environment:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres-rooms:5432/${ROOMS_DB:-rooms}
    SPRING_DATASOURCE_USERNAME: ${ROOMS_DB_USER:-rooms}
    SPRING_DATASOURCE_PASSWORD: ${ROOMS_DB_PASSWORD:-changeme}
    JWT_SECRET: ${JWT_SECRET}
    JWT_EXPIRY_SECONDS: ${JWT_EXPIRY_SECONDS:-900}
  depends_on:
    postgres-rooms:
      condition: service_healthy
```

**Why separate container over separate database in shared instance**:
- True isolation: separate credentials, separate data volume, independent backups
- Reflects real microservices architecture where each service owns its DB host
- No risk of accidentally reading auth tables from rooms-service

**Alternatives Considered**:
- **Separate database (same PostgreSQL container)**: Simpler setup but `POSTGRES_DB`
  can only specify one default database; requires manual `CREATE DATABASE` in init
  scripts. Still shares a PostgreSQL process — not true isolation. Rejected.

---

## 3. entry-auth-service Changes (JWT Addition)

`entry-auth-service` is updated minimally to issue JWTs. Session management is
unchanged — the browser still uses the server-side session cookie for the auth
service. JWT is an additional artifact issued at join time for inter-service use.

**New `JwtService` in entry-auth-service**:
```java
@Service
public class JwtService {
    @Value("${JWT_SECRET}") private String secret;
    @Value("${JWT_EXPIRY_SECONDS:900}") private int expirySeconds;

    public String generateToken(String username) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
            .subject(username)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirySeconds * 1000L))
            .signWith(key)
            .compact();
    }
}
```

**Updated `JoinResponse`** (new `token` field):
```json
{
  "username": "alice",
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**New endpoint: `GET /auth/refresh-token`**:
- Requires valid session cookie
- Returns `{ "token": "..." }` with a fresh JWT
- Used by frontend before rooms API calls when JWT is near expiry

**Impact on existing frontend**: The Next.js app must:
1. Store the JWT from `JoinResponse.token` in memory (React state / context)
2. Pass `Authorization: Bearer <token>` on all calls to `/rooms/**`
3. Call `GET /auth/refresh-token` when JWT is near expiry (e.g., within 60 seconds)

---

## 4. API Gateway: RoomsProxyController

**Decision**: Add `RoomsProxyController` to the existing gateway, mirroring
`AuthProxyController` exactly. Maps `@RequestMapping("/rooms/**")` to `rooms-service`
via a `RestClient` configured with `ROOMS_SERVICE_URL` env var.

The gateway forwards the `Authorization: Bearer` header as-is (it already copies all
headers except `Host` and `Content-Length` in the proxy pattern). No gateway-level
auth validation needed — `rooms-service` validates the JWT itself.

**`application.yml` addition**:
```yaml
services:
  auth:
    url: ${ENTRY_AUTH_SERVICE_URL:http://entry-auth-service:8080}
  rooms:
    url: ${ROOMS_SERVICE_URL:http://rooms-service:8080}
```

---

## 5. Spring Security in rooms-service (Stateless JWT)

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthFilter jwtAuthFilter) {
        return http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .build();
    }
}
```

No `@EnableJdbcHttpSession` — rooms-service creates no sessions.

---

## 6. Room Naming Sequence & Creation Cap

Unchanged from original design (see plan.md). `user_room_stats` table with two
counters: `rooms_created_count` (monotonic, for naming) and `active_rooms_count`
(bounded 0–10, for cap enforcement). Both updated atomically in the same transaction
as the room insert.

---

## 7. jOOQ Code Generation

Identical configuration to `entry-auth-service`. DDL-based generation from Flyway
migration scripts. Package: `com.privchat.rooms.jooq`.
