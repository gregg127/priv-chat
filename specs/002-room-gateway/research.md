# Research: Room Gateway

**Feature**: 002-room-gateway
**Date**: 2026-03-31
**Phase**: Phase 0 — Technology Research

---

## 1. JWT Authentication Strategy

**Decision**: RS256 JWT signed with an RSA private key held exclusively by
`entry-auth-service`. `rooms-service` fetches the public key once at startup via a
JWKS endpoint (`GET /auth/jwks`) and caches it locally for signature verification.
No shared secret between services — only the public key is distributed.

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
// entry-auth-service build.gradle (issue + sign):
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

// rooms-service build.gradle (validate only):
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
```

**Key pair configuration** (entry-auth-service only):
- RSA 2048-bit key pair loaded from env vars at startup
- `JWT_PRIVATE_KEY`: base64-encoded PKCS#8 PEM private key (entry-auth-service only)
- `JWT_PUBLIC_KEY`: base64-encoded X.509 PEM public key (entry-auth-service only)
- If not set, service generates an ephemeral key pair at startup (dev mode — all issued
  JWTs become invalid on restart)

**JWKS endpoint** (new, entry-auth-service):
- `GET /auth/jwks` — unauthenticated, returns public key in JWK Set format:
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

**Issue flow** (entry-auth-service):
1. `POST /auth/join` succeeds → `JwtService.generateToken(username)` → JWT string
   (signed RS256 with private key, header `"alg":"RS256","kid":"priv-chat-1"`)
2. JWT returned in `JoinResponse.token` field (new field alongside existing `username`)
3. `GET /auth/refresh-token` (new endpoint) → validates session → returns fresh JWT

**Validation flow** (rooms-service):
1. On startup: `JwksClient` fetches `GET /auth/jwks`, extracts `RSAPublicKey`, caches it
2. `JwtAuthFilter extends OncePerRequestFilter` intercepts every request
3. Extracts `Authorization: Bearer <token>` header
4. `JwtService.validateToken(token)` → verifies RS256 signature using cached public key + expiry
5. On success: injects `UsernamePasswordAuthenticationToken(username, ...)` into
   `SecurityContextHolder` — no session created (`SessionCreationPolicy.STATELESS`)
6. On failure: returns `401 Unauthorized`

**Why RS256 over HS256**:
- No shared secret between services — only the public key is distributed
- Private key never leaves `entry-auth-service` — if `rooms-service` is compromised,
  it cannot forge tokens
- Industry standard for microservices JWT auth (compatible with OAuth2/OIDC patterns)
- Per-request inter-service calls are acceptable — JWKS fetch is cached, not per-request

**Revocation tradeoff**:
- JWTs are stateless — cannot be invalidated before expiry
- Mitigated by 15-minute expiry: max exposure window on logout is 15 minutes
- Frontend discards the JWT immediately on logout
- The constitution explicitly accepts this: "token-based with short-lived credentials"

**Alternatives Considered**:
- **HS256 (shared secret)**: Simpler, but creates tight coupling — both services must
  share `JWT_SECRET`; if rooms-service is compromised, attacker can forge tokens. Rejected.
- **Token introspection per request**: Fully decoupled but adds ~1 network hop latency
  per rooms API call + failure dependency on entry-auth-service availability. Rejected in
  favour of cached JWKS (local validation with minimal coupling).
- **Shared Spring Session JDBC**: Violates database isolation requirement. Rejected.
- **API Key**: No expiry, no user identity encoded, not industry-standard. Rejected.

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
    ENTRY_AUTH_SERVICE_URL: http://entry-auth-service:8080
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
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    @Value("${JWT_EXPIRY_SECONDS:900}") private int expirySeconds;

    public JwtService(
        @Value("${JWT_PRIVATE_KEY:}") String privateKeyB64,
        @Value("${JWT_PUBLIC_KEY:}") String publicKeyB64
    ) {
        if (privateKeyB64.isBlank()) {
            // Dev mode: generate ephemeral key pair
            KeyPair kp = generateRsaKeyPair();
            this.privateKey = (RSAPrivateKey) kp.getPrivate();
            this.publicKey  = (RSAPublicKey)  kp.getPublic();
        } else {
            this.privateKey = loadPrivateKey(privateKeyB64);
            this.publicKey  = loadPublicKey(publicKeyB64);
        }
    }

    public String generateToken(String username) {
        return Jwts.builder()
            .subject(username)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirySeconds * 1000L))
            .signWith(privateKey)  // RS256 inferred from RSAPrivateKey
            .compact();
    }

    public RSAPublicKey getPublicKey() { return publicKey; }
}
```

**New `JwksController` in entry-auth-service** (`GET /auth/jwks`):
```java
@RestController
public class JwksController {
    private final JwtService jwtService;

    @GetMapping("/auth/jwks")
    public Map<String, Object> jwks() {
        RSAPublicKey pub = jwtService.getPublicKey();
        return Map.of("keys", List.of(Map.of(
            "kty", "RSA", "use", "sig", "alg", "RS256",
            "kid", "priv-chat-1",
            "n", Base64.getUrlEncoder().withoutPadding()
                      .encodeToString(pub.getModulus().toByteArray()),
            "e", Base64.getUrlEncoder().withoutPadding()
                      .encodeToString(pub.getPublicExponent().toByteArray())
        )));
    }
}
```
This endpoint is unauthenticated — `rooms-service` (and any future service) can fetch
the public key without credentials.

**Updated `JoinResponse`** (new `token` field):
```json
{
  "username": "alice",
  "token": "eyJhbGciOiJSUzI1NiJ9..."
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

## 5a. JWKS Client in rooms-service

On startup, `rooms-service` fetches the public key from `entry-auth-service`:

```java
@Component
public class JwksClient {
    private volatile RSAPublicKey cachedPublicKey;
    private final String jwksUrl;

    public JwksClient(@Value("${ENTRY_AUTH_SERVICE_URL}") String baseUrl,
                      RestClient restClient) {
        this.jwksUrl = baseUrl + "/auth/jwks";
        this.cachedPublicKey = fetchPublicKey(restClient);
    }

    public RSAPublicKey getPublicKey() { return cachedPublicKey; }

    private RSAPublicKey fetchPublicKey(RestClient client) {
        // Parse JWKS JSON → decode n, e → reconstruct RSAPublicKey
        // Uses standard java.security.spec.RSAPublicKeySpec
    }
}
```

`JwtService` in rooms-service uses `JwksClient.getPublicKey()` for all token validation.
The fetch happens once at startup (not per request) — `rooms-service` depends on
`entry-auth-service` being healthy before starting (enforced via Docker Compose
`depends_on: condition: service_healthy`).

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
