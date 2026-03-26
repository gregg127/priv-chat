# Quickstart: Network Entry Gate

**Feature**: 001-network-entry-gate
**Date**: 2026-03-26

Get the full stack running locally in under 5 minutes.

## Prerequisites

- Docker 25+ and Docker Compose v2 (`docker compose` command)
- Git
- A modern browser (Chrome, Firefox, Safari, or Edge — current version)

## 1. Clone and configure

```bash
git clone <repo-url> priv-chat
cd priv-chat
git checkout 001-network-entry-gate
```

Create a local environment file for secrets:

```bash
cp implementation/.env.example implementation/.env
```

Edit `implementation/.env` and set the shared network password:

```env
# Network password — share this out-of-band with members
NETWORK_PASSWORD=change-me-before-use

# PostgreSQL
POSTGRES_DB=privchat
POSTGRES_USER=privchat
POSTGRES_PASSWORD=localdev-secret

# Session timeout (seconds) — default 86400 = 24 hours
SESSION_TIMEOUT_SECONDS=86400
```

> ⚠️ Never commit `.env` to version control. It is listed in `.gitignore`.

## 2. Start the stack

```bash
cd implementation
docker compose up --build
```

First build takes ~2–3 minutes (JVM image download + Gradle build + Next.js build). Subsequent
starts are fast.

**Services started**:

| Service | Internal port | Exposed port |
|---------|--------------|--------------|
| `postgres` | 5432 | 5432 (localhost) |
| `entry-auth-service` | 8080 | — (internal only) |
| `api-gateway` | 8080 | 8080 (localhost) |
| `frontend` | 3000 | 3000 (localhost) |

## 3. Verify everything is healthy

```bash
docker compose ps
# All services should show "healthy" or "running"

# Check entry-auth-service health endpoint (via gateway)
curl http://localhost:8080/auth/actuator/health
# Expected: {"status":"UP"}
```

## 4. Open the portal

Navigate to: **http://localhost:3000**

You should see the entry gate page with:
- A username text field
- A password field
- A "Join network" button

## 5. Join the network

1. Enter any display name (e.g., `alice`)
2. Enter the `NETWORK_PASSWORD` you set in `.env`
3. Click **Join network**
4. You should be redirected to the portal interior

## 6. Test rejection

1. Click the back button (or open a new incognito tab)
2. Enter any username + a wrong password
3. Click **Join network**
4. You should see: *"Incorrect network password"*

## 7. Test rate limiting

Attempt 5 incorrect passwords in quick succession. On the 6th attempt you
should see: *"Too many attempts — please try again in N minutes"*

## 8. Verify session persistence

1. Join the network (correct password)
2. Close the browser tab
3. Reopen http://localhost:3000
4. You should land directly in the portal interior (no re-authentication)

## Stopping the stack

```bash
docker compose down
# To also remove the database volume:
docker compose down -v
```

## Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f entry-auth-service
docker compose logs -f api-gateway
```

Security audit events are logged to stdout by the entry-auth-service and also
persisted to the `security_audit_log` table in PostgreSQL.

```bash
# Query audit log directly
docker compose exec postgres psql -U privchat -d privchat \
  -c "SELECT event_type, ip_address, username, occurred_at FROM security_audit_log ORDER BY occurred_at DESC LIMIT 20;"
```

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| `entry-auth-service` fails to start | PostgreSQL not ready | Wait 10 s and retry; check `depends_on` health check |
| "Incorrect network password" with correct password | `NETWORK_PASSWORD` mismatch between `.env` and what you typed | Check `.env` value |
| Port 8080 already in use | Another service on that port | Change gateway port in `docker-compose.override.yml` |
| Rate limit not resetting | In-memory bucket — restart entry-auth-service | `docker compose restart entry-auth-service` |
