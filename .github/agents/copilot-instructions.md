# priv-chat Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-04-01

## Stack

**Backend**: Java 25 (LTS), Spring Boot 4.0.4, Spring Security, Spring Session JDBC, jOOQ 3.20.4, Flyway 11.20.3, JJWT 0.12.6
**Frontend**: TypeScript, React 19, Next.js 15 (App Router)
**Database**: PostgreSQL 17 (two separate instances — one per service)
**Build**: Gradle (Groovy DSL) for Java services; npm for frontend
**Tests**: JUnit 5, Testcontainers, Spring Boot Test (backend); Next.js build check (frontend)

## Active Features

| Branch | Feature | Status |
|--------|---------|--------|
| 001-network-entry-gate | Network Entry Gate (login/session) | ✅ Merged |
| 002-room-gateway | Room Gateway (room CRUD + gateway UI) | 🔧 In progress |

## Project Structure

```text
implementation/
├── api-gateway/          # Spring Boot REST proxy → routes /auth/** and /rooms/**
├── services/
│   ├── entry-auth-service/   # Auth, sessions, JWT issuance, JWKS endpoint
│   └── rooms-service/        # Room CRUD microservice (separate DB)
├── frontend/             # Next.js 15 app (App Router)
└── docker-compose.yml    # Full stack: postgres, postgres-rooms, services, frontend
specs/
├── 001-network-entry-gate/
└── 002-room-gateway/
.specify/memory/constitution.md   # Project constitution (non-negotiable)
```

## Commands

```bash
# Run all rooms-service tests
cd implementation/services/rooms-service && ./gradlew test

# Run all entry-auth-service tests
cd implementation/services/entry-auth-service && ./gradlew test

# Build frontend
cd implementation/frontend && npm run build

# Start full stack
docker compose -f implementation/docker-compose.yml up --build

# Serve journal docs
npm run journal
```

## Key Conventions

- JWT auth: `entry-auth-service` issues RS256 JWTs; `rooms-service` validates locally via JWKS (no per-request inter-service calls)
- Session persistence: Spring Session JDBC in `entry-auth-service`; frontend restores JWT via `GET /auth/refresh-token` on page load
- Separate DBs: `postgres` for auth/sessions; `postgres-rooms` for rooms — no shared credentials
- Room polling: 10-second interval (intentional — prevents interval-restart memory leak in React)
- API routing: browser → Next.js rewrite → api-gateway → service (rooms-service has no published port)
- Creator-only mutations: username taken from JWT `sub` claim, never from request body

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->

## Active Technologies
- TypeScript (frontend + backend) + Signal Protocol (`@signalapp/libsignal-client` via WASM), WebCrypto API (browser), WebSocket (native browser + server), Node.js backend (003-room-page-chat)
- PostgreSQL (ciphertext messages, key bundles, room metadata); IndexedDB (client-side key material, session state) (003-room-page-chat)

## Recent Changes
- 003-room-page-chat: Added TypeScript (frontend + backend) + Signal Protocol (`@signalapp/libsignal-client` via WASM), WebCrypto API (browser), WebSocket (native browser + server), Node.js backend
