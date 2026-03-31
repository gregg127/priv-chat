# priv-chat Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-03-31

## Active Technologies
- PostgreSQL 17 — session store + security audit log (001-network-entry-gate)
- Java 25 (LTS) + Spring Web, Spring Security, Spring Session JDBC, (002-room-gateway)
- PostgreSQL 17 — shared instance; `rooms-service` owns `rooms`, (002-room-gateway)

- Java 25 LTS (backend), TypeScript / React 19 (frontend) + Spring Boot 4.0.4, Spring Cloud Gateway 5.0.x (Oakwood), (001-network-entry-gate)

## Project Structure

```text
backend/
frontend/
tests/
```

## Commands

npm test && npm run lint

## Code Style

Java 25 LTS (backend), TypeScript / React 19 (frontend): Follow standard conventions

## Recent Changes
- 002-room-gateway: Added Java 25 (LTS) + Spring Web, Spring Security, Spring Session JDBC,
- 001-network-entry-gate: Added Java 25 LTS (backend), TypeScript / React 19 (frontend) + Spring Boot 4.0.4, Spring Cloud Gateway 5.0.x (Oakwood),

- 001-network-entry-gate: Added Java 25 LTS (backend), TypeScript / React 19 (frontend) + Spring Boot 4.0.4, Spring Cloud Gateway 5.0.x (Oakwood),

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
