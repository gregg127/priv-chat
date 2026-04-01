# Data Model: Room Page with Chat

**Feature**: `003-room-page-chat` | **Date**: 2026-04-01

---

## Entities

### Room

Represents an invite-only private chat space. Persists indefinitely.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | UUID | PK | Immutable |
| `name` | string | NOT NULL, max 100 chars | Plaintext (documented exception per constitution; room metadata is not zero-knowledge) |
| `owner_id` | UUID FK → User | NOT NULL | Current owner; updated on ownership transfer |
| `created_at` | timestamp | NOT NULL | Immutable |
| `updated_at` | timestamp | NOT NULL | Last metadata change |

**Indexes**: `owner_id`

---

### RoomMember

Junction table representing a user's invite-based membership in a room. The `joined_at` timestamp is the **history visibility boundary** — a member can only decrypt and view messages sent at or after this time.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `room_id` | UUID FK → Room | PK (composite) | |
| `user_id` | UUID FK → User | PK (composite) | |
| `joined_at` | timestamp | NOT NULL | Invite timestamp; history boundary |
| `join_seq` | bigint | NOT NULL | The room `seq` value at time of invite; messages with `seq < join_seq` are inaccessible |
| `invited_by` | UUID FK → User | NOT NULL | Who issued the invite (must be room owner at time) |

**Indexes**: `room_id`, `user_id`, `(room_id, joined_at)` (for ownership transfer query)

**Constraint**: `invited_by` must be the `Room.owner_id` at time of insert (enforced at application layer).

---

### Message

Stores E2E encrypted message ciphertext. The server never holds plaintext. The `seq` is the per-room ordering cursor used for catch-up and display ordering.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | bigint | PK, auto-increment | |
| `room_id` | UUID FK → Room | NOT NULL | |
| `seq` | bigint | NOT NULL, UNIQUE (room_id, seq) | Per-room monotonic sequence; assigned by server |
| `sender_id` | UUID FK → User | NOT NULL | Stored for routing / display attribution; does not reveal content |
| `ciphertext` | bytea | NOT NULL | SenderKey-encrypted Signal Protocol ciphertext |
| `client_message_id` | UUID | NOT NULL, UNIQUE (room_id, client_message_id) | Client-generated; used for deduplication on retry |
| `server_timestamp` | timestamp | NOT NULL | Server receipt time; used for display |
| `deleted_at` | timestamp | NULLABLE | Set by room owner delete action; soft-delete |

**Indexes**: `(room_id, seq)`, `(room_id, client_message_id)`, `(sender_id)`, `deleted_at` (partial index for active messages)

**Ordering**: Always `ORDER BY seq ASC`. `server_timestamp` is secondary / display only.

**History access**: Queries MUST include `WHERE seq >= (SELECT join_seq FROM room_members WHERE room_id = ? AND user_id = ?)`.

---

### KeyBundle

Server-side storage for Signal Protocol public key material uploaded by each user/device. Server stores **only public keys** — private keys never leave the client.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | bigint | PK, auto-increment | |
| `user_id` | UUID FK → User | NOT NULL | |
| `device_id` | integer | NOT NULL, DEFAULT 1 | Device identifier (for multi-device, future) |
| `identity_key` | bytea | NOT NULL | Curve25519 public identity key; immutable per device |
| `signed_prekey_id` | integer | NOT NULL | Version of the signed prekey |
| `signed_prekey` | bytea | NOT NULL | Curve25519 signed prekey |
| `signed_prekey_signature` | bytea | NOT NULL | Ed25519 signature of signed prekey using identity key |
| `created_at` | timestamp | NOT NULL | |
| `updated_at` | timestamp | NOT NULL | Updated on signed prekey rotation |

**Indexes**: `(user_id, device_id)` UNIQUE

---

### OneTimePreKey

Single-use ephemeral keys for X3DH. Consumed (deleted) when fetched. The server maintains a pool per user/device; client replenishes when pool runs low.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | bigint | PK, auto-increment | |
| `user_id` | UUID FK → User | NOT NULL | |
| `device_id` | integer | NOT NULL, DEFAULT 1 | |
| `key_id` | integer | NOT NULL | Client-assigned ID within the prekey batch |
| `public_key` | bytea | NOT NULL | Curve25519 public prekey |
| `created_at` | timestamp | NOT NULL | |

**Indexes**: `(user_id, device_id, key_id)` UNIQUE

**Lifecycle**: Deleted server-side immediately after `GET /api/keys/bundles/{userId}` fetches it. If pool is empty, X3DH proceeds without a one-time prekey (reduced but acceptable security).

---

## State Transitions

### Room Ownership

```
[Created] → owner_id = creator
    ↓ (owner account deleted)
[Ownership Transfer] → owner_id = member with MIN(joined_at)
    ↓ (if no other members remain)
[Orphaned / Deleted] → room deleted (no members = no purpose)
```

### Message Lifecycle

```
[Draft]     → client composing, not sent
[Sent]      → client sends via WebSocket; awaiting server ack
[Stored]    → server assigns seq + server_timestamp, stores ciphertext; ack sent to sender
[Delivered] → fanned out to all connected room subscribers
[Deleted]   → owner sets deleted_at; removed from all member views
```

### OneTimePreKey Lifecycle

```
[Uploaded]  → client uploads batch to server
[Available] → in pool, awaiting claim
[Claimed]   → fetched by a peer for X3DH; deleted immediately from server
```

---

## Client-Side State (IndexedDB)

The following structures live **exclusively on the client** and are never transmitted to the server:

| Store | Key | Value | Notes |
|-------|-----|-------|-------|
| `identity` | `"self"` | `{ identityKeyPair, registrationId }` | Permanent; generated on first login |
| `signedPreKeys` | `signedPreKeyId` | `SignedPreKeyRecord` | Rotated periodically |
| `preKeys` | `preKeyId` | `PreKeyRecord` | Ephemeral; consumed on session init |
| `sessions` | `address` (userId:deviceId) | `SessionRecord` | Double Ratchet session state per peer |
| `senderKeys` | `(distributionId, address)` | `SenderKeyRecord` | SenderKey state per room per sender |
| `roomCursors` | `roomId` | `{ lastSeenSeq, joinSeq }` | Catch-up cursor; history boundary |
| `messages` | `(roomId, seq)` | `{ ciphertext, decrypted, senderId, serverTimestamp }` | Local message cache (decrypted) |

---

## Relationships Summary

```
User ─┬─< RoomMember >─┬─ Room
      │                 │    └─ owner_id → User
      └─< Message        │
      └─< KeyBundle      │
      └─< OneTimePreKey  │
                         └─< Message (room_id)
```

- One User → many RoomMembers (rooms they belong to)
- One Room → many RoomMembers (members)
- One Room → many Messages (ordered by seq)
- One User → one KeyBundle per device
- One User → many OneTimePreKeys (pool)
- One RoomMember.join_seq → history visibility lower bound on Messages
