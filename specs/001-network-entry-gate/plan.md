# Implementation Plan: Network Entry Gate

**Branch**: `001-network-entry-gate` | **Date**: 2026-03-26 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-network-entry-gate/spec.md`

## Summary

Build the portal entry gate: a React landing page where users choose a display
name and enter a shared network password to gain access. The backend is an
`entry-auth-service` (Spring Boot / Java 25) behind a Spring Cloud Gateway. Sessions
are stored server-side in PostgreSQL. IP-based rate limiting (5 failures / 10 min)
and structured security-event logging are first-class requirements per the
project constitution.

## Technical Context

**Language/Version**: Java 25 LTS (backend), TypeScript / React 19 (frontend)
**Primary Dependencies**: Spring Boot 4.0.4, Spring Cloud Gateway 5.0.x (Oakwood),
  Spring Session JDBC, Bucket4j 8.x (rate limiting), jOOQ 3.20.x (data access),
  nu.studer.jooq 9.0 (Gradle codegen plugin), Next.js 15 + React 19 (frontend),
  Node.js 22 (Next.js runtime in Docker)
**Storage**: PostgreSQL 17 — session store + security audit log
**Testing**: JUnit 5 + Mockito + Spring Boot Test (backend);
  Jest + React Testing Library (Next.js) (frontend)
  and single-node deployment
**Project Type**: Microservices web application (entry-auth-service + API gateway +
  React frontend; additional services in future features)
**Performance Goals**: Auth response < 500 ms p95; page load < 2 s on standard
  broadband
**Constraints**: All services containerized; no secrets in source control;
  TLS terminated at API gateway; session cookie HttpOnly + Secure + SameSite=Strict
**Scale/Scope**: Private network; dozens to low hundreds of concurrent users

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Privacy by Design | ✅ Pass | Entry gate handles display names only — no message content, no private keys. Server stores session ID + username; zero E2EE material on server. |
| II. Security First | ✅ Pass | Rate limiting, HttpOnly/Secure/SameSite=Strict cookie, server-side sessions, security event logging, threat model included below. Secrets via env vars only. |
| III. Test-First | ✅ Pass | TDD mandatory; tests written before implementation. Security paths (rate limiter, session validation, password check) require dedicated test suites. |
| IV. Web-First | ✅ Pass | React SPA in browser; no native app required. localStorage used only for non-secret display name pre-fill. |
| V. Simplicity | ⚠️ Justified | Microservices adds infrastructure complexity — see Complexity Tracking below. |

### Threat Model

| Threat | Mitigation | Residual Risk |
|--------|-----------|---------------|
| Brute-force network password | IP rate limiting: 5 failures / 10 min lockout (FR-009) | Distributed attack from many IPs — acceptable for v1 |
| Session hijacking | HttpOnly + Secure + SameSite=Strict cookie; server-side session invalidation | XSS mitigated by CSP headers (planned in hardening pass) |
| Man-in-the-middle | TLS 1.2+ required at API gateway | Certificate management is operator responsibility |
| Network password in source code | Password injected as environment variable at runtime; never committed | Operator must manage env vars securely |
| Replay of session cookie | Server-side session with expiry; cookie not reusable after server-side invalidation | None — fully mitigated |
| SQL injection | jOOQ parameterised queries only; no raw SQL string concat | None |
| Username as attack vector | Username is a display-name only; not used in queries without parameterisation | None |
| Rate-limit bypass via IP spoofing | X-Forwarded-For header parsed behind trusted gateway only | Sophisticated attacker with many IPs remains residual risk |

## Project Structure

### Documentation (this feature)

```text
specs/001-network-entry-gate/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── entry-auth-service.md
└── tasks.md             # Phase 2 output (speckit.tasks)
```

### Source Code (repository root)

```text
implementation/
├── docker-compose.yml
├── docker-compose.override.yml     # local dev overrides (hot reload, etc.)
│
├── api-gateway/                    # Spring Cloud Gateway
│   ├── src/main/resources/
│   │   └── application.yml         # routing rules
│   ├── Dockerfile
│   └── build.gradle
│
├── services/
│   └── entry-auth-service/               # Spring Boot — session auth
│       ├── src/
│       │   ├── main/java/com/privchat/auth/
│       │   │   ├── controller/     # AuthController (join, logout, check)
│       │   │   ├── service/        # AuthService, RateLimitService, AuditLogService
│       │   │   ├── model/          # SecurityAuditLog (plain record; no JPA)
│       │   │   └── config/         # SecurityConfig, SessionConfig
│       │   └── test/java/com/privchat/auth/
│       │       ├── controller/
│       │       ├── service/
│       │       └── integration/
│       ├── src/main/resources/
│       │   ├── application.yml
│       │   └── db/migration/       # Flyway migrations
│       ├── Dockerfile
│       └── build.gradle
│
├── frontend/                       # Next.js + React + TypeScript
│   ├── src/
│   │   ├── app/
│   │   │   └── page.tsx            # Entry gate page (SSR)
│   │   ├── components/
│   │   │   └── JoinForm/
│   │   └── lib/
│   │       └── authApi.ts          # API calls to gateway
│   ├── Dockerfile                  # multi-stage: build → node:22-alpine next start
│   ├── next.config.ts
│   └── package.json
│
└── db/
    └── init/                       # PostgreSQL init (Flyway handles schema)
```

**Structure Decision**: Microservices layout with separate top-level directories
per service. `api-gateway` is a peer service, not nested under `services/`,
because it is infrastructure — not a domain service. Database migrations live
inside each service (Flyway) to keep schema ownership with the service.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Microservices (multiple projects for one feature) | User explicitly requires microservices as foundation for future chat services (auth, messaging, presence, etc.) | Monolith would require painful extraction later when Signal Protocol services are added |
| API Gateway service | Required for routing, TLS termination, and future cross-cutting concerns (auth header injection, rate limiting at edge) | Direct client-to-service calls break when services multiply and makes CORS + TLS management unmanageable |

