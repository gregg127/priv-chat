# Research: Network Entry Gate

**Feature**: 001-network-entry-gate  
**Date**: 2026-03-26  
**Phase**: Phase 0 — Technology Research

---

## 1. Java 25 + Spring Boot Version

**Decision**: Spring Boot 4.0.x (latest: 4.0.4) on Java 25 (LTS)

**Rationale**: Spring Boot 4.0.x is built on Spring Framework 7 and officially supports Java 17–25. It is the recommended version for new projects targeting Java 25, with active open-source support through end of 2026. Spring Framework 7 ships with first-class virtual thread support, improved pattern matching, and GraalVM native image for JDK 25.

**Alternatives Considered**:
- Spring Boot 3.5.x: Also supports Java 25, but is an older generation and will reach end-of-life sooner. No reason to start a new project on 3.x.

**Key Notes**:
- Enable virtual threads with `spring.threads.virtual.enabled=true` (supported since Spring Boot 3.2, first-class in 4.x). Each HTTP request gets its own virtual thread — no need for reactive/WebFlux to handle high concurrency.
- Use **Records** for all DTOs and request/response models (immutable, zero-boilerplate).
- Use **Sealed classes + records** for auth result modeling, e.g.:
  ```java
  sealed interface AuthResult permits AuthSuccess, AuthFailure, RateLimited {}
  record AuthSuccess(String sessionId) implements AuthResult {}
  record AuthFailure(String reason) implements AuthResult {}
  record RateLimited(long retryAfterSeconds) implements AuthResult {}
  ```
- Use **pattern matching** switch expressions for exhaustive handling of sealed hierarchies.
- **Scoped Values** (Java 21+, finalized by Java 25) useful for propagating request context (IP, correlation ID) through the call stack without `ThreadLocal`.
- Java 25 is an LTS release; use `eclipse-temurin:25-jre-alpine` as the runtime Docker base image.

---

## 2. Spring Cloud Gateway

**Decision**: Spring Cloud Gateway 5.0.x (Spring Cloud release train: 2025.1.x / Oakwood) using the WebMVC variant

**Rationale**: Spring Cloud 2025.1.x (Oakwood) is the release train aligned with Spring Boot 4.0.x. The Gateway 5.0.x generation splits into explicit WebMVC and WebFlux artifacts — we use WebMVC since entry-auth-service and future services are standard Servlet-based Spring Boot apps. No need for reactive complexity.

**Alternatives Considered**:
- **Spring Cloud 2025.0.x**: Aligned with Spring Boot 3.5.x — incompatible with Boot 4.x.
- **Generic `spring-cloud-starter-gateway`**: Deprecated in 2025.0.0+ in favour of the explicit `-server-webmvc` / `-server-webflux` starters.
- **Nginx as gateway**: Simpler but lacks dynamic routing, filter chains, and Spring Security integration needed for future cross-cutting concerns.

**Key Notes**:
- Dependency to use:
  ```xml
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
  </dependency>
  ```
- BOM import: `org.springframework.cloud:spring-cloud-dependencies:2025.1.x`
- Route configuration in `application.yml` (preferred over env-var injection for readability and version control):
  ```yaml
  spring:
    cloud:
      gateway:
        server:
          webmvc:
            routes:
              - id: entry-auth-service
                uri: http://entry-auth-service:8081
                predicates:
                  - Path=/api/auth/**
                filters:
                  - StripPrefix=2
  ```
- In docker-compose, services reference each other by service name. No service discovery (Eureka/Consul) needed for a small fixed-service topology.
- Gateway should be the **only** service with a published port. All backend services communicate over an internal Docker network.
- Add `X-Forwarded-For` header passthrough to allow entry-auth-service to see the real client IP for rate limiting.

---

## 3. Session Management

**Decision**: Spring Session JDBC backed by PostgreSQL, with `DefaultCookieSerializer` for full cookie attribute control

**Rationale**: Server-side sessions stored in PostgreSQL satisfy the requirement for persistence across restarts and future horizontal scaling of entry-auth-service. Spring Session JDBC auto-configures with Spring Boot and provides a standard schema. Using `DefaultCookieSerializer` (rather than `application.properties` alone) gives explicit control over `HttpOnly`, `Secure`, and `SameSite=Strict`.

**Alternatives Considered**:
- **Spring Session Redis**: Requires an additional Redis service in docker-compose. Overkill for v1 with a single entry-auth-service instance; PostgreSQL already required for other data.
- **In-memory `HttpSession`**: Lost on restart; not acceptable for a persistent portal.
- **JWT stateless tokens**: Stateless JWTs cannot be invalidated server-side. Violates the requirement for server-side sessions.

**Key Notes**:
- Dependency: `org.springframework.session:spring-session-jdbc`
- Spring Boot auto-creates the session schema on startup (`spring.session.jdbc.initialize-schema=always` in dev; use Flyway migration in prod).
- Session schema tables: `SPRING_SESSION` and `SPRING_SESSION_ATTRIBUTES`.
- Cookie configuration via Java bean (preferred for `SameSite=Strict`):
  ```java
  @Bean
  public DefaultCookieSerializer cookieSerializer() {
      DefaultCookieSerializer s = new DefaultCookieSerializer();
      s.setCookieName("PRIV_SESSION");
      s.setUseHttpOnlyCookie(true);
      s.setUseSecureCookie(true);
      s.setSameSite("Strict");
      s.setCookiePath("/");
      return s;
  }
  ```
- Set session timeout: `spring.session.timeout=30m`
- Enable scheduled cleanup of expired sessions: `spring.session.jdbc.cleanup-cron=0 */5 * * * *`
- Session ID is a cryptographically random UUID; never expose it in URLs or response bodies.
- The gateway must forward the session cookie unchanged; do not strip `Cookie` headers at the gateway layer.

---

## 4. Rate Limiting in entry-auth-service

**Decision**: Bucket4j (in-memory, `ConcurrentHashMap` per IP) inside entry-auth-service as a `HandlerInterceptor`

**Rationale**: The requirement is 5 failed attempts per IP per 10 minutes. For v1 with a single entry-auth-service instance, in-memory rate limiting is correct — no Redis or shared state required. Bucket4j is a well-maintained, production-proven Java library implementing the token bucket algorithm. It is zero-dependency (no Redis, no external service), and can be migrated to distributed mode (Bucket4j-Redis) later if entry-auth-service is scaled out.

**Alternatives Considered**:
- **Spring Cloud Gateway `RequestRateLimiter` filter**: Requires Redis as a backing store. Adds infrastructure complexity for a single-instance v1.
- **Spring Security `FailureHandler` with manual counter in DB**: Heavier — involves DB writes on every failed login. Fine for auditing but slower for hot-path enforcement.
- **Resilience4j rate limiter**: Designed for outbound calls / circuit breaking, not per-IP inbound enforcement.

**Key Notes**:
- Dependency: `com.bucket4j:bucket4j-core:8.15.0` (or latest 8.x)
- Implement as a `HandlerInterceptor` on the `/api/auth/join` endpoint only (not all routes).
- Use `ConcurrentHashMap<String, Bucket>` keyed by IP. Extract IP from `X-Forwarded-For` header (set by the gateway) with fallback to `request.getRemoteAddr()`.
- Bucket configuration for "5 attempts per 10 minutes" using a **greedy refill** over a fixed 10-minute window:
  ```java
  Bandwidth limit = Bandwidth.builder()
      .capacity(5)
      .refillGreedy(5, Duration.ofMinutes(10))
      .build();
  return Bucket.builder().addLimit(limit).build();
  ```
- On limit exceeded: return HTTP 429 with `Retry-After` header and log a `RATE_LIMITED` security event.
- **Memory leak mitigation**: Schedule periodic eviction of buckets last accessed more than 20 minutes ago (using `Caffeine` cache with expiry as the map backing, or a simple scheduled cleanup task).
- Rate limiting applies to _attempts_ (including failed ones), not just failures — simplest and most conservative policy for v1.

---

## 5. Security Event Logging

**Decision**: Dedicated `security_audit_log` table managed by JPA + `AuditEventService` bean; SLF4J/Logback for operational logging only

**Rationale**: Security events (failed auth, successful join, rate-limit triggers) are structured business data, not operational log noise. A dedicated PostgreSQL table allows querying, dashboards, and compliance review. Logback DB appender writes semi-structured text into generic schema columns — harder to query and report on. The JPA approach gives full control over the schema and enables future alerting queries (e.g., "IPs with >3 lockouts in 1 hour").

**Alternatives Considered**:
- **Logback `DBAppender`**: Fast to set up, but unstructured. Not SQL-friendly for security analysis. Not transactionally linked to business operations. Rejected for primary security auditing.
- **External log aggregator (ELK, Loki)**: Correct long-term solution, but out of scope for v1. Can be added later as a secondary sink.
- **Spring Boot Actuator `AuditEventRepository`**: In-memory only by default; DB-backed implementation requires custom code equivalent to the JPA approach anyway.

**Key Notes**:
- Schema:
  ```sql
  CREATE TABLE security_audit_log (
    id          BIGSERIAL PRIMARY KEY,
    event_type  VARCHAR(50)  NOT NULL CHECK (event_type IN ('JOIN_SUCCESS','JOIN_FAILURE','RATE_LIMITED')),
    ip_address  VARCHAR(45)  NOT NULL,
    username    VARCHAR(64),            -- null for JOIN_FAILURE and RATE_LIMITED events
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
  );
  CREATE INDEX idx_security_audit_log_ip ON security_audit_log (ip_address, occurred_at DESC);
  CREATE INDEX idx_security_audit_log_type ON security_audit_log (event_type, occurred_at DESC);
  ```
- Use `INET` PostgreSQL type for IP addresses (supports IPv4 and IPv6, enables subnet queries).
- Inject an `AuditLogRepository extends JpaRepository<AuditLog, Long>` and call it from the auth service layer after each event.
- Keep audit writes **outside the main auth transaction** (use `@Transactional(propagation = REQUIRES_NEW)`) so a failed DB write doesn't roll back the auth response.
- SLF4J/Logback continues to be used for operational debug/info/error output; audit events are additionally written to the DB table.
- Do **not** log plaintext passwords or session IDs in any log.

---

## 6. Docker Compose Structure

**Decision**: Four services — `frontend`, `api-gateway`, `entry-auth-service`, `postgres` — on an isolated internal network, with only `frontend` (Nginx) and `api-gateway` exposing host ports

**Rationale**: Network isolation prevents direct external access to backend services. The frontend Nginx container serves the React SPA and proxies `/api/**` to the gateway. This mirrors a production topology and avoids CORS issues during development.

**Alternatives Considered**:
- **Nginx as the sole entry point (no gateway)**: Loses Spring Cloud Gateway's routing, filter, and future cross-cutting capabilities (auth token forwarding, CORS policy, circuit breakers).
- **Exposing entry-auth-service directly**: Bypasses the gateway; breaks the microservices topology.

**Key Notes**:
- Recommended `docker-compose.yml` structure:
  ```yaml
  services:
    postgres:
      image: postgres:17-alpine
      environment:
        POSTGRES_DB: privchat
        POSTGRES_USER: privchat
        POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      volumes:
        - postgres_data:/var/lib/postgresql/data
      healthcheck:
        test: ["CMD-SHELL", "pg_isready -U privchat"]
        interval: 5s
        timeout: 5s
        retries: 10
      networks: [internal]

    entry-auth-service:
      build: ./entry-auth-service
      environment:
        SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/privchat
        SPRING_DATASOURCE_USERNAME: privchat
        SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      depends_on:
        postgres:
          condition: service_healthy
      networks: [internal]

    api-gateway:
      build: ./api-gateway
      ports:
        - "8080:8080"
      depends_on:
        - entry-auth-service
      networks: [internal, external]

    frontend:
      build: ./frontend
      ports:
        - "80:80"
        - "443:443"
      networks: [external]

  networks:
    internal:
      internal: true
    external: {}

  volumes:
    postgres_data:
  ```
- `internal: true` on the internal network prevents containers from reaching the internet directly.
- Secrets via `.env` file (never committed); reference with `${VAR}`.
- Use `condition: service_healthy` for postgres dependency to avoid startup race conditions.
- Frontend Nginx proxies `/api/` to `http://api-gateway:8080/api/` using `proxy_pass` in `nginx.conf` — this keeps the gateway as the single API entry point even from the browser's perspective.

---

## 7. React Frontend (TypeScript)

**Decision**: Next.js 15 + React 19 + TypeScript; served in production via multi-stage Docker build → `node:22-alpine` running `next start`

**Rationale**: Next.js is explicitly requested. It provides SSR for the entry gate page (fast initial load, no blank-page flash), the App Router with file-based routing, and a clean TypeScript-first developer experience. SSR also means the page is fully rendered before JavaScript executes — beneficial for the entry form's accessibility and load performance.

**Alternatives Considered**:
- **Vite + React SPA**: Lighter weight, but user explicitly chose Next.js. Would require client-side rendering only.
- **Create React App**: Legacy, effectively unmaintained in 2026.
- **Remix / Astro**: Overkill; adds unfamiliar patterns for a focused form page.

**Key Notes**:
- Bootstrap: `npx create-next-app@latest frontend --typescript --app --no-tailwind`
- Use **App Router** (`src/app/`) — Next.js 15 default
- Multi-stage `Dockerfile`:
  ```dockerfile
  FROM node:22-alpine AS builder
  WORKDIR /app
  COPY package*.json ./
  RUN npm ci
  COPY . .
  RUN npm run build

  FROM node:22-alpine AS runner
  WORKDIR /app
  ENV NODE_ENV=production
  COPY --from=builder /app/.next/standalone ./
  COPY --from=builder /app/.next/static ./.next/static
  EXPOSE 3000
  CMD ["node", "server.js"]
  ```
  Requires `output: 'standalone'` in `next.config.ts`.
- API base URL: use `NEXT_PUBLIC_API_BASE_URL` env var (available at build time and runtime). For docker-compose dev, use Next.js `rewrites` in `next.config.ts` to proxy `/auth/**` → API gateway, avoiding CORS.
- Security headers: set via `next.config.ts` `headers()` function — `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`.
- `localStorage` usage for username pre-fill is client-side only; never store session tokens in `localStorage`.

---

## Threat Model

| Threat | Mitigation | Residual Risk |
|--------|-----------|---------------|
| **Brute force (password guessing)** | Bucket4j rate limiter: 5 attempts / 10 min per IP; HTTP 429 with `Retry-After`; `RATE_LIMITED` audit event | Distributed attack from many IPs bypasses single-IP limit; mitigated in future by CAPTCHA or shared-secret rotation |
| **Credential stuffing (shared network password)** | Shared network password can be rotated; rate limiting slows automated tooling; audit log alerts on spike in failed attempts | If the shared password leaks, all protection depends on rate limiting until rotation |
| **Session hijacking** | `HttpOnly` prevents JS access; `Secure` enforces HTTPS-only transmission; `SameSite=Strict` blocks CSRF-based session theft | Compromised TLS or endpoint device still exposes cookie |
| **Session fixation** | Spring Session regenerates session ID on successful authentication (`SessionManagementFilter` / explicit `sessionRegistry.invalidateHttpSessions`) | Low residual risk with Spring Security defaults |
| **CSRF (Cross-Site Request Forgery)** | `SameSite=Strict` cookie attribute is the primary mitigation for a same-origin SPA. Add Spring Security CSRF token for non-SameSite clients if required | Modern browsers enforce `SameSite=Strict`; very low residual risk |
| **MITM (Man-in-the-Middle)** | `Secure` cookie flag; HTTPS enforced at Nginx; HSTS header (`Strict-Transport-Security: max-age=63072000; includeSubDomains`) | If TLS certificate is compromised or user ignores browser warning, MITM is possible |
| **SQL Injection** | Spring Data JPA / parameterised queries everywhere; no string-concatenated SQL; `INET` type for IP prevents injection via that field | Negligible with parameterised queries; validate and sanitise all inputs at controller layer |
| **Replay attacks (stolen session cookie)** | Short session TTL (30 min idle timeout); absolute session expiry enforced server-side; session invalidated on logout | Stolen cookie valid until TTL; out-of-band session revocation not in v1 scope |
| **Secret exposure in source/logs** | Passwords and secrets via `.env` (not committed); `application.yml` uses `${ENV_VAR}` references; audit log never writes raw passwords or full session IDs | Developer error (committing `.env`) is the main residual risk; mitigated by `.gitignore` and pre-commit hooks |
| **Enumeration of valid usernames** | Login endpoint returns identical error message and HTTP status (401) for both wrong password and unknown user; timing attack mitigated by constant-time comparison | Advanced timing analysis at network layer still theoretically possible |
| **Clickjacking** | `X-Frame-Options: DENY` and `Content-Security-Policy: frame-ancestors 'none'` set by Nginx | Low residual risk; browsers honour these headers |
| **Sensitive data in `localStorage`** | Only username (not session token, not password) stored in `localStorage` for pre-fill; session cookie is `HttpOnly` and never accessible to JS | Username leakage from shared browser; acceptable low-sensitivity data |
