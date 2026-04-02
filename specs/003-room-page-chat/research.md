# Research: Room Page with Chat

**Feature**: `003-room-page-chat` | **Date**: 2026-04-01

---

## Decision 1: Signal Protocol Library

**Decision**: Use `@signalapp/libsignal-client` (the official Signal library, v0.90+)

**Rationale**: This is the official Signal Foundation library, actively maintained, AGPL-licensed, with native TypeScript types, WebAssembly support for browsers, and Rust-backed cryptography. It implements all required primitives: X3DH, Double Ratchet, SenderKey, and Sealed Sender. Community alternatives (`signal-protocol` npm package) are deprecated and unmaintained.

**Alternatives considered**:
- `signal-protocol` (npm) — deprecated, pure JS, no longer maintained. Rejected.
- Custom WebCrypto implementation — would require re-implementing audited cryptography. Rejected (Constitution: use well-audited libraries).

---

## Decision 2: Group Messaging Scheme — SenderKey

**Decision**: Use Signal Protocol **SenderKey** scheme for room (group) messaging, not per-member Double Ratchet.

**Rationale**: SenderKey is the Signal Protocol mechanism for group chat. It uses a single sender chain key per sender per room, distributed individually to each member via a sealed Double Ratchet session. This provides:
- **Efficiency**: O(1) encrypt per message (vs O(N) for per-member Double Ratchet)
- **Forward secrecy**: SenderChainKey ratchets forward with every message; old keys cannot be derived
- **History boundary**: New members receive `SenderKeyDistributionMessage` with the *current* chain iteration — they cannot reverse the ratchet to read prior messages

**Alternatives considered**:
- Per-member Double Ratchet: would require encrypting each message N times (once per member). Rejected for groups of up to 50 members.
- Shared room symmetric key: no forward secrecy, no per-member join boundaries. Rejected (Constitution Principle I).

---

## Decision 3: History Access Boundary for New Members

**Decision**: History access is enforced **cryptographically by the SenderKey scheme** — not just by server-side filtering.

**Rationale**: When a new member is invited, the room owner sends them a `SenderKeyDistributionMessage` containing the *current* chain iteration. The SenderChainKey ratchets forward-only (HMAC-based); it is impossible to derive earlier iteration keys. So new members are cryptographically unable to decrypt pre-invite messages. Server-side enforce the same boundary (query `WHERE seq >= member_join_seq`) as an additional guard.

**Alternatives considered**:
- Server filtering only: would still expose ciphertext to the new member, which is bad practice. Rejected.
- Re-encrypt history with new member's key: complex, costly, violates "messages are for intended recipients at time of sending". Rejected.

---

## Decision 4: Key Storage — IndexedDB on Client

**Decision**: All private key material (identity key, session state, SenderKey state) stored exclusively in **IndexedDB** on the client device.

**Rationale**: IndexedDB is the only persistent storage option in browsers that can hold structured binary data without transmitting it to the server. Keys must survive page reloads (unlike sessionStorage/memory). The Constitution explicitly prohibits transmitting private keys to the server.

**Implementation note**: IndexedDB entries for sensitive key material should use the Web Crypto API to encrypt at rest using a key derived from the user's login credential (optional enhancement for future feature).

**Alternatives considered**:
- `localStorage`: string-only, no encryption at rest, synchronous API. Rejected.
- Session memory only: keys lost on page reload, breaking reconnect. Rejected.
- Server-side key escrow: violates zero-knowledge server principle. Rejected (Constitution Principle I).

---

## Decision 5: WebSocket Session Model — Multiplexed Single Connection

**Decision**: Single WebSocket connection per user, multiplexed across multiple rooms via subscription messages.

**Rationale**: Invite-only rooms mean users are in a small set of rooms (~5–20 typical). A single multiplexed connection is more efficient than N TCP connections, reduces handshake overhead, and simplifies session management. Room routing is handled via `roomId` in message frames. Authentication happens once on connect.

**Alternatives considered**:
- One WebSocket per room: multiplies connection overhead by room count. Rejected for typical invite-only use.
- HTTP polling: high latency, incompatible with ≤1s message delivery (SC-002). Rejected.

---

## Decision 6: Message Ordering — Server Sequence Numbers

**Decision**: Server assigns a strictly-increasing per-room `seq` (sequence number) to every stored message. Client displays messages ordered by `seq`.

**Rationale**: Client clocks cannot be trusted (skew, manipulation). Server-assigned sequence numbers guarantee stable, deterministic ordering. The `seq` value doubles as the catch-up cursor (`lastSeenSeq`) for reconnect delivery.

**Alternatives considered**:
- Client timestamps only: vulnerable to clock skew. Rejected.
- Logical clocks (Lamport): overkill for a single-server model. Deferred as future enhancement if multi-region replication is needed.

---

## Decision 7: Reconnect Catch-Up — Seq-Based Pull

**Decision**: On reconnect, client sends `{ type: "reconnect", roomId, lastSeenSeq }`. Server queries `WHERE roomId = ? AND seq > lastSeenSeq` and delivers in batches.

**Rationale**: `lastSeenSeq` is stored in IndexedDB per room. Because messages are persistent and ordered by `seq`, the server can efficiently replay exactly the missed messages. This satisfies FR-018 without any client-side message buffer.

---

## Decision 8: Observability — Metadata-Only, Privacy-Preserving

**Decision**: All server-side logging and metrics are restricted to **metadata signals** only. No message content, no ciphertext, no key material, no sender/recipient pairs beyond routing necessity.

*(Full detail pending observability agent — see addendum below)*

**Safe to log (server-side)**:
- Connection events: `user_connected`, `user_disconnected`, `connection_duration_ms` (no room context in log)
- Message throughput: count per room per minute (no user attribution in aggregate metrics)
- Delivery latency: time from server receipt to last-subscriber fanout (no message content)
- WebSocket error rates: disconnect reason codes (no user identity)
- Key bundle events: `prekey_bundle_uploaded`, `prekey_bundle_fetched` (no key material)
- Security events: `auth_failed`, `invalid_token`, `rate_limit_exceeded`, `unauthorized_room_access` (with userId, no message content)
- Invite events: `invite_sent`, `invite_accepted` (owner userId, room ID — metadata only)
- Ownership transfer: `ownership_transferred` (from_userId, to_userId, room_id)

**Never log**:
- Message ciphertext or plaintext
- Private keys, session keys, SenderKey material
- Full sender/recipient pairs in application logs (only routing layer uses this)
- Message content metadata (length is borderline — omit for now)
- Decryption success/failure per-message (could leak timing info)

**Structured log format** (safe fields):
```json
{
  "timestamp": "2026-04-01T14:00:00Z",
  "level": "info",
  "event": "message_stored",
  "roomId": "room-uuid",
  "seq": 105,
  "sizeBytes": 256
}
```
Note: `userId` omitted from `message_stored` events to prevent building sender timelines.

---

## Decision 9: Rate Limiting

**Decision**: Token bucket per user (10 msg/sec global), per room (100 msg/sec), per user-per-room (5 msg/sec). Enforced server-side on WebSocket message receipt.

**Rationale**: Private invite-only rooms have lower abuse risk than public channels, but rate limiting is still required to prevent accidental flooding and denial-of-service. These limits are generous for normal chat usage.

---

## Decision 10: Ownership Transfer on Owner Deletion

**Decision**: When the room owner's account is deleted, ownership transfers to the **member with the earliest invite timestamp** (longest-standing member). The transfer is atomic with account deletion.

**Rationale**: Preserves room continuity and chat history for remaining members. The longest-standing member is the most natural successor (they were trusted earliest). Requires storing `invitedAt` timestamp per room membership record.

---

## Key Server Endpoints Required

The Signal Protocol requires a key distribution server. Endpoints required for this feature:

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/keys/bundles/{userId}` | GET | Fetch PreKey bundle for X3DH session init (invite flow) |
| `/api/keys/bundles` | POST | Upload/refresh key bundle (identity, signed prekey, OTP keys) |
| `/api/keys/prekeys/replenish` | POST | Upload new batch of one-time prekeys |

One-time prekeys are deleted server-side after retrieval (single-use guarantee).

---

## Decision 8 (Updated): Observability — Metadata-Only, Privacy-Preserving

**Decision**: Server-side observability is strictly limited to **aggregate, anonymized metadata signals**. No per-user event streams, no message content, no timing data that could reveal communication patterns.

**Safe to log**:

| Category | Safe signals |
|----------|-------------|
| Connection health | Connection duration, heartbeat failure rate, reconnect success rate — all aggregated, no user linkage |
| Message throughput | Total messages/sec aggregate; size distribution in buckets (0-512B, 512B-4KB, etc.) — no per-user counts |
| Delivery latency | p50/p95/p99 aggregated over 1-minute buckets; no per-user latency |
| Security events | `auth_failed`, `rate_limit_exceeded`, `unauthorized_room_access` — with `sha256(userId)[0:12]` hash, no plaintext user ID |
| Key bundle events | `prekey_bundle_uploaded`, `prekey_bundle_fetched` — count only, no key material |
| Room lifecycle | `room_created`, `ownership_transferred` — room ID + timestamp bucket |
| WebSocket errors | Disconnect reason codes, error type distribution — no user identity |

**Never log**:
- Message ciphertext or plaintext
- Private keys, session keys, SenderKey material
- Full user IDs (use `sha256(userId)[0:12]` in security logs only)
- Sender→recipient pairs (reveals relationship graph)
- Per-message timestamps per user (enables traffic analysis)
- Message content length per individual message

**Log format** (safe fields):
```json
{
  "timestamp_bucket": "2026-04-01T14:00",
  "event": "message_stored",
  "room_id_hash": "sha256(roomId)[0:12]",
  "seq": 105,
  "size_bucket": "512B-4KB"
}
```

**Rationale**: Signal and WhatsApp both minimize server-side persistence to prevent traffic analysis attacks. Aggregate metrics protect operational needs (capacity planning, SLA monitoring) without enabling relationship inference or metadata correlation attacks.

**Alternatives considered**:
- Per-user metrics: would enable traffic analysis (user A sent N messages at time T = identifies communication pattern). Rejected.
- No observability at all: prevents operational monitoring, SLA validation, and security alerting. Rejected.
