# Implementation Plan: Room Page with Chat

**Branch**: `003-room-page-chat` | **Date**: 2026-04-01 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/003-room-page-chat/spec.md`

## Summary

Build the in-room experience: a page showing room name and live member list, real-time E2E-encrypted group chat using the Signal Protocol (SenderKey scheme), and an owner-only invite flow. The server is zero-knowledge — it stores and relays ciphertext only. Message history is scoped to each member's invite date. Reconnect catch-up delivers all missed messages automatically. Observability is limited to metadata-safe signals (no message content, no keys).

## Technical Context

**Language/Version**: TypeScript (frontend + backend)  
**Primary Dependencies**: Signal Protocol (`@signalapp/libsignal-client` via WASM), WebCrypto API (browser), WebSocket (native browser + server), Node.js backend  
**Storage**: PostgreSQL (ciphertext messages, key bundles, room metadata); IndexedDB (client-side key material, session state)  
**Testing**: Vitest (unit + integration); Playwright (E2E browser); known Signal Protocol test vectors for crypto unit tests  
**Target Platform**: Modern evergreen browsers (Chrome, Firefox, Safari); Node.js server (Linux)  
**Project Type**: Web application (frontend SPA + backend API/WebSocket server)  
**Performance Goals**: Message delivery ≤1s (SC-002); member list update ≤2s (SC-003); room page load ≤2s (SC-001); invite reflected ≤10s (SC-008)  
**Constraints**: Zero-knowledge server (no plaintext message content); keys never leave device; WebCrypto for all browser crypto; TLS 1.2+ for all transport  
**Scale/Scope**: Up to ~50 members per room; private invite-only; persistent message history per member invite boundary

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Privacy by Design | ✅ PASS | Signal Protocol (X3DH + Double Ratchet + SenderKey) mandated. Server stores ciphertext only. Keys generated and stored on device (IndexedDB). History boundary enforced at invite timestamp. |
| II. Security First | ✅ PASS | Threat model included below. Auth via existing session (feature 001). Signal Protocol library is audited (`@signalapp/libsignal-client`). Rate limiting specified. Security-relevant events logged without content. |
| III. Test-First | ✅ PASS | TDD enforced. Crypto operations tested with known Signal Protocol test vectors. Full send/receive lifecycle integration tests required. |
| IV. Web-First | ✅ PASS | Browser SPA with WebCrypto API. WebSocket for real-time. IndexedDB for key storage. Offline reconnect/catch-up designed. |
| V. Simplicity | ✅ PASS | No speculative features. Each user story independently deliverable. Signal Protocol is justified complexity (Security First / Privacy by Design). SenderKey scheme chosen over per-member Double Ratchet for group scalability. |

### Threat Model

| Threat | Mitigation |
|--------|-----------|
| Server reads message content | Server stores ciphertext only; SenderKey scheme; WebCrypto decryption in browser |
| Non-member accesses room | FR-010: server validates membership on every WebSocket message and REST request |
| Replay attack on messages | Signal Double Ratchet provides forward secrecy; server sequence numbers detect replays |
| New member accesses pre-invite history | Invite timestamp stored server-side; history queries scoped to `seq >= member_join_seq` |
| Owner account deleted, room hijacked | FR-019: ownership transfers to longest-standing member (earliest invite timestamp) |
| Key material leaked in logs | Observability strictly metadata-only; no ciphertext, no key material in any log |
| WebSocket connection spoofing | JWT auth required before any subscription; server validates token on connect |
| Invite spam / member enumeration | Only owner can invite; username existence check returns generic error to prevent enumeration |
| Rate limit bypass | Per-user + per-room token bucket; enforced server-side, not client-side |

## Project Structure

### Documentation (this feature)

```text
specs/003-room-page-chat/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
├── contracts/           ← Phase 1 output
│   ├── websocket.md     ← WebSocket message protocol
│   ├── rest-api.md      ← REST endpoints (invite, room, key server)
│   └── signal-key-server.md ← Key bundle contract
└── tasks.md             ← Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
backend/
├── src/
│   ├── models/          # Room, Member, Message, Invite, KeyBundle
│   ├── services/
│   │   ├── room/        # Room management, membership, ownership transfer
│   │   ├── message/     # Store/retrieve ciphertext messages, sequence management
│   │   ├── invite/      # Invite flow, username lookup, member grant
│   │   ├── keyserver/   # Signal key bundle registration and distribution
│   │   └── websocket/   # Connection management, fanout, presence, rate limiting
│   ├── api/
│   │   ├── rooms.ts     # REST: room detail, member list
│   │   ├── invites.ts   # REST: send invite
│   │   ├── messages.ts  # REST: history fetch (paginated)
│   │   └── keys.ts      # REST: key bundle endpoints
│   └── ws/
│       └── handler.ts   # WebSocket message router
└── tests/
    ├── unit/            # Service-level tests, crypto test vectors
    ├── integration/     # Full message lifecycle, invite flow, reconnect
    └── contract/        # API contract tests

frontend/
├── src/
│   ├── pages/
│   │   └── room/        # Room page (details + chat)
│   ├── components/
│   │   ├── RoomHeader/  # Room name, member list
│   │   ├── ChatArea/    # Message history, send input
│   │   ├── MemberList/  # Live presence list
│   │   └── InvitePanel/ # Owner-only invite input
│   ├── services/
│   │   ├── signal/      # Signal Protocol: SenderKey session management, encrypt/decrypt
│   │   ├── websocket/   # Connection, reconnect, catch-up, subscription
│   │   ├── keystore/    # IndexedDB key material persistence
│   │   └── room/        # Room state, member list, invite API calls
│   └── store/           # Client-side state (messages, members, connection status)
└── tests/
    ├── unit/            # Signal service, keystore, UI components
    ├── integration/     # Room page flows
    └── e2e/             # Playwright: send message, invite member, reconnect
```

**Structure Decision**: Web application with separate `backend/` and `frontend/` trees. Signal Protocol crypto runs exclusively in the browser (frontend/src/services/signal). The server never receives plaintext or keys.

## Complexity Tracking

| Complexity | Why Needed | Simpler Alternative Rejected Because |
|------------|-----------|--------------------------------------|
| Signal Protocol (SenderKey) | Privacy by Design (Principle I) + Constitution mandate | Plaintext storage or server-side encryption would violate zero-knowledge server requirement |
| IndexedDB key persistence | Keys must never leave device (Constitution Security Requirements) | sessionStorage would lose keys on tab close; cookies would transmit to server |
| Per-member history boundary | New members must not read pre-invite messages (FR-009, Q4 clarification) | No simpler model satisfies this without per-member invite timestamp tracking |
| WebSocket catch-up (seq-based) | FR-018: missed messages delivered on reconnect | Polling would introduce latency and battery drain; REST-only history misses real-time gap |
