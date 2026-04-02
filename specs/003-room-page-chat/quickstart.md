# Quickstart: Room Page with Chat

**Feature**: `003-room-page-chat` | **Date**: 2026-04-01

---

## What This Feature Builds

An in-room experience consisting of:

1. **Room detail view** — name, live member list (presence-aware)
2. **Real-time encrypted chat** — Signal Protocol SenderKey group messaging
3. **Owner invite flow** — username-based invite granting room access
4. **Persistent history** — messages visible from each member's invite date onward
5. **Reconnect catch-up** — missed messages automatically delivered on reconnect

The server is **zero-knowledge**: it stores and routes ciphertext only.

---

## Cryptographic Architecture Overview

```
Room Chat Message Flow:
─────────────────────────────────────────────────────────
Browser (Alice)                   Server              Browser (Bob)
     │                               │                     │
     │  encrypt(plaintext,            │                     │
     │    SenderKey[room])            │                     │
     │──── WS: message ─────────────►│                     │
     │                               │ store ciphertext     │
     │                               │ (seq assigned)       │
     │◄─── WS: message_ack ──────────│                     │
     │                               │──── WS: message_new ►│
     │                               │                     │
     │                               │                decrypt(ciphertext,
     │                               │                  SenderKey[room])
     │                               │                     │
─────────────────────────────────────────────────────────
Server sees: room_id, seq, sender_id, ciphertext (opaque), timestamp
Server does NOT see: message content
```

---

## Key Components

### Backend

| Module | Responsibility |
|--------|---------------|
| `services/keyserver` | Store/serve public key bundles (identity, signed prekey, OTP pool) |
| `services/invite` | Validate username, create `RoomMember` record with `join_seq`, trigger key bundle fetch |
| `services/message` | Assign `seq`, store ciphertext, enforce `join_seq` history boundary |
| `services/websocket` | Auth, subscribe/unsubscribe, fanout, presence heartbeat, catch-up delivery |
| `services/room` | Room detail, ownership transfer on owner deletion |

### Frontend

| Module | Responsibility |
|--------|---------------|
| `services/signal` | SenderKey session management, `encrypt()`, `decrypt()`, `createDistributionMessage()` |
| `services/keystore` | IndexedDB persistence for all Signal Protocol state (identity, sessions, sender keys) |
| `services/websocket` | Connection lifecycle, reconnect, `lastSeenSeq` tracking, message dispatch |
| `pages/room` | Orchestrates RoomHeader, MemberList, ChatArea, InvitePanel |

---

## Signal Protocol Session Setup for a New Member

When the room owner invites `bob`:

```
1. Owner fetches Bob's PreKey bundle  →  GET /api/v1/keys/bundles/{bob_id}
2. Owner performs X3DH with Bob's bundle  →  establishes 1:1 Double Ratchet session
3. Owner creates SenderKeyDistributionMessage  →  libsignal.GroupSessionBuilder.createSenderKeyDistributionMessage()
4. Owner seals it for Bob via Double Ratchet  →  libsignal.SessionCipher.encrypt()
5. Owner sends sealed message  →  WS: sender_key_distribution { recipientUserId: bob_id, distributionMessage }
6. Server routes to Bob  →  WS: sender_key_distribution (delivered when Bob connects)
7. Bob processes it  →  libsignal.GroupSessionBuilder.processSenderKeyDistributionMessage()
8. Bob can now decrypt all subsequent room messages
```

---

## Data Flow: Send Message

```
1. User types message
2. frontend/services/signal: encrypt(plaintext) → base64 SenderKeyMessage ciphertext
3. frontend/services/websocket: send { type:"message", roomId, clientMessageId, ciphertext }
4. Server: validate membership → assign seq → store → broadcast message_new
5. Server: send message_ack to sender { clientMessageId, seq, serverTimestamp }
6. Other clients receive message_new → signal.decrypt(ciphertext) → display
```

---

## Data Flow: Reconnect Catch-Up

```
1. Client loses WebSocket connection
2. Client reconnects, re-authenticates, re-subscribes with lastSeenSeq from IndexedDB
3. Server: SELECT ... WHERE seq > lastSeenSeq AND seq >= join_seq ORDER BY seq
4. Server: send catchup_batch (paginated if large) → catchup_complete
5. Client: decrypt each message → update IndexedDB → update UI
6. Real-time stream resumes from latestSeq
```

---

## History Boundary Enforcement

```
Member join creates:
  RoomMember { userId, roomId, joinedAt, joinSeq = current max(seq) }

All message queries scoped to:
  WHERE seq >= join_seq   (server-enforced)

Cryptographically:
  SenderKeyDistributionMessage delivered with current chain iteration N
  Bob cannot derive keys for iterations < N (ratchet is forward-only)
```

---

## Observability Signals (Privacy-Safe)

| What to measure | How | Never include |
|-----------------|-----|---------------|
| Message delivery latency | p50/p95/p99 aggregated per minute | Per-user timing |
| WebSocket health | Heartbeat failure rate, reconnect rate | User identity |
| Message throughput | Total messages/sec aggregate | Per-room or per-user counts |
| Security events | Auth failures with `sha256(userId)[0:12]` | Plaintext user ID |
| Invite success rate | Aggregate count | Who invited whom |

---

## Testing Strategy

| Layer | What to test |
|-------|-------------|
| Unit: `signal` service | Encrypt/decrypt round-trip with known Signal test vectors; SenderKey distribution and processing |
| Unit: `message` service | `join_seq` boundary enforcement; seq assignment; deduplication on `clientMessageId` |
| Unit: `invite` service | Username lookup; ownership validation; `join_seq` capture |
| Integration | Full send/receive lifecycle (two browser contexts); reconnect catch-up; invite → decrypt flow |
| E2E (Playwright) | Room page renders; message appears for both users; owner invite flow; non-member blocked |
| Security | Non-member cannot subscribe; pre-invite messages inaccessible; rate limit enforced |

---

## Prerequisites

- Feature `001` (authentication) must be complete — session JWT required for all WebSocket and REST calls
- Feature `002` (Room Gateway) must be updated to show invite-only rooms — but this feature's room page can be developed independently by seeding test room membership directly

---

## Dependencies

| Dependency | Purpose |
|-----------|---------|
| `@signalapp/libsignal-client` | Signal Protocol: X3DH, Double Ratchet, SenderKey, WASM for browser |
| PostgreSQL | Server-side: room, member, message, key bundle storage |
| IndexedDB | Client-side: all Signal Protocol key material and session state |
| WebSocket (native) | Real-time message delivery, presence, catch-up |
| TLS 1.2+ | Transport security (required by Constitution) |
