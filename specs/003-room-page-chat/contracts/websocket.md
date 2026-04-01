# WebSocket Protocol Contract

**Feature**: `003-room-page-chat` | **Date**: 2026-04-01  
**Endpoint**: `wss://{host}/ws`  
**Auth**: JWT token required (sent as first message after connect)

---

## Connection Lifecycle

```
Client connects → sends auth → subscribes to room(s) → exchanges messages → disconnects
```

All frames are JSON. The `roomId` field scopes every message to a specific room.

---

## Client → Server Messages

### `auth`
Must be the first message sent after WebSocket connection is established.

```json
{
  "type": "auth",
  "token": "<JWT>"
}
```

**Response**: `auth_ack` or `error` with code `unauthorized`.

---

### `subscribe`
Subscribe to a room's real-time stream. Triggers catch-up delivery of missed messages since `lastSeenSeq`.

```json
{
  "type": "subscribe",
  "roomId": "uuid",
  "lastSeenSeq": 104
}
```

- `lastSeenSeq`: last sequence number the client has seen; `0` or omitted to load from `join_seq`
- Server validates that the authenticated user is a member of `roomId`

**Response**: `subscribed` frame followed by `catchup_batch` (if messages missed), then `catchup_complete`.

---

### `unsubscribe`

```json
{
  "type": "unsubscribe",
  "roomId": "uuid"
}
```

---

### `message`
Send an E2E encrypted message to a room.

```json
{
  "type": "message",
  "roomId": "uuid",
  "clientMessageId": "uuid",
  "ciphertext": "<base64-encoded Signal SenderKeyMessage>"
}
```

- `clientMessageId`: client-generated UUID; server uses for idempotent deduplication on retry
- `ciphertext`: base64-encoded Signal Protocol `SenderKeyMessage` (encrypted for the room's SenderKey)

**Response**: `message_ack` from server to sender, then `message_new` broadcast to all room subscribers.

---

### `heartbeat`
Keepalive. Send every 30 seconds.

```json
{
  "type": "heartbeat"
}
```

**Response**: No response. Server updates last-seen timestamp internally.

---

### `sender_key_distribution`
Deliver a `SenderKeyDistributionMessage` to a newly invited member. Sent by the room owner (or any existing member) when a new member joins, individually encrypted for the recipient.

```json
{
  "type": "sender_key_distribution",
  "roomId": "uuid",
  "recipientUserId": "uuid",
  "distributionMessage": "<base64-encoded SenderKeyDistributionMessage sealed for recipient>"
}
```

---

## Server → Client Messages

### `auth_ack`

```json
{
  "type": "auth_ack",
  "userId": "uuid"
}
```

---

### `subscribed`

```json
{
  "type": "subscribed",
  "roomId": "uuid",
  "joinSeq": 42
}
```

`joinSeq`: the sequence number at which this member was invited (history lower bound).

---

### `catchup_batch`
Delivered after `subscribe` if `lastSeenSeq < currentSeq`. May be sent in multiple batches for large gaps.

```json
{
  "type": "catchup_batch",
  "roomId": "uuid",
  "messages": [
    {
      "id": 1001,
      "seq": 105,
      "senderId": "uuid",
      "ciphertext": "<base64>",
      "serverTimestamp": "2026-04-01T14:00:00Z"
    }
  ],
  "hasMore": false
}
```

---

### `catchup_complete`

```json
{
  "type": "catchup_complete",
  "roomId": "uuid",
  "latestSeq": 110
}
```

---

### `message_ack`
Sent to the original sender only after the message is stored.

```json
{
  "type": "message_ack",
  "clientMessageId": "uuid",
  "id": 1001,
  "seq": 110,
  "serverTimestamp": "2026-04-01T14:00:01Z"
}
```

---

### `message_new`
Broadcast to all room subscribers when a new message is stored.

```json
{
  "type": "message_new",
  "roomId": "uuid",
  "id": 1001,
  "seq": 110,
  "senderId": "uuid",
  "ciphertext": "<base64>",
  "serverTimestamp": "2026-04-01T14:00:01Z"
}
```

---

### `member_joined`
Broadcast to room when a new member's invite is confirmed.

```json
{
  "type": "member_joined",
  "roomId": "uuid",
  "userId": "uuid",
  "joinedAt": "2026-04-01T14:00:00Z"
}
```

---

### `member_left`
Broadcast when a member disconnects or goes offline (heartbeat timeout).

```json
{
  "type": "member_left",
  "roomId": "uuid",
  "userId": "uuid"
}
```

---

### `presence_update`
Sent to a newly subscribed client to convey current online/offline status of room members.

```json
{
  "type": "presence_update",
  "roomId": "uuid",
  "members": [
    { "userId": "uuid", "status": "online" },
    { "userId": "uuid", "status": "offline" }
  ]
}
```

---

### `message_deleted`
Broadcast to room when the owner deletes a message.

```json
{
  "type": "message_deleted",
  "roomId": "uuid",
  "seq": 105
}
```

---

### `ownership_transferred`
Broadcast to room when ownership changes (owner account deleted).

```json
{
  "type": "ownership_transferred",
  "roomId": "uuid",
  "newOwnerUserId": "uuid"
}
```

---

### `error`

```json
{
  "type": "error",
  "code": "unauthorized | not_member | rate_limit_exceeded | invalid_message | not_found",
  "message": "Human-readable description",
  "retryAfterMs": 1000
}
```

`retryAfterMs` only present for `rate_limit_exceeded`.

---

## Rate Limits

| Limit | Value |
|-------|-------|
| Per-user global | 10 messages/sec |
| Per-room total | 100 messages/sec |
| Per-user per-room | 5 messages/sec |

Exceeded limits return `error` with code `rate_limit_exceeded` and `retryAfterMs`.
