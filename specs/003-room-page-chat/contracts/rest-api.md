# REST API Contract

**Feature**: `003-room-page-chat` | **Date**: 2026-04-01  
**Base URL**: `/api/v1`  
**Auth**: `Authorization: Bearer <JWT>` required on all endpoints  
**Content-Type**: `application/json`

---

## Room Endpoints

### `GET /rooms/:roomId`
Get room details and member list. Requires caller to be a member of the room.

**Response 200**:
```json
{
  "id": "uuid",
  "name": "string",
  "ownerId": "uuid",
  "createdAt": "ISO8601",
  "members": [
    {
      "userId": "uuid",
      "joinedAt": "ISO8601",
      "isOwner": true
    }
  ]
}
```

**Errors**: `403 Forbidden` (not a member), `404 Not Found`

---

### `GET /rooms/:roomId/messages`
Fetch paginated message history. Returns only messages at or after the caller's `join_seq`.

**Query params**:
| Param | Type | Description |
|-------|------|-------------|
| `before_seq` | integer | Return messages with `seq < before_seq` (for pagination) |
| `limit` | integer | Max messages to return (default: 50, max: 100) |

**Response 200**:
```json
{
  "messages": [
    {
      "id": 1001,
      "seq": 105,
      "senderId": "uuid",
      "ciphertext": "<base64>",
      "serverTimestamp": "ISO8601"
    }
  ],
  "hasMore": true,
  "oldestSeq": 55
}
```

**Errors**: `403 Forbidden` (not a member), `404 Not Found`

---

## Invite Endpoints

### `POST /rooms/:roomId/invites`
Owner invites a user by username. Only the room owner may call this endpoint.

**Request body**:
```json
{
  "username": "string"
}
```

**Response 201**:
```json
{
  "userId": "uuid",
  "roomId": "uuid",
  "joinedAt": "ISO8601",
  "joinSeq": 110
}
```

**Errors**:

| Status | Code | Condition |
|--------|------|-----------|
| `403 Forbidden` | `not_owner` | Caller is not the room owner |
| `404 Not Found` | `user_not_found` | Username does not exist (generic — prevents enumeration) |
| `409 Conflict` | `already_member` | User is already a member of the room |

> **Note**: `user_not_found` uses the same response shape as a generic 404 to prevent username enumeration attacks.

---

## Key Server Endpoints

### `GET /keys/bundles/:userId`
Fetch a PreKey bundle for a user. Used by the room owner to initiate an X3DH session before sending a `SenderKeyDistributionMessage` to a new invitee. Consumes one one-time prekey (deleted after retrieval).

**Response 200**:
```json
{
  "userId": "uuid",
  "deviceId": 1,
  "identityKey": "<base64 Curve25519 public key>",
  "signedPreKey": {
    "keyId": 42,
    "publicKey": "<base64>",
    "signature": "<base64 Ed25519 signature>"
  },
  "oneTimePreKey": {
    "keyId": 7,
    "publicKey": "<base64>"
  }
}
```

`oneTimePreKey` may be absent if the pool is exhausted. X3DH proceeds without it (lower security, still functional per Signal spec).

**Errors**: `404 Not Found` (user not registered)

---

### `POST /keys/bundles`
Upload or refresh the caller's key bundle.

**Request body**:
```json
{
  "deviceId": 1,
  "identityKey": "<base64>",
  "signedPreKey": {
    "keyId": 42,
    "publicKey": "<base64>",
    "signature": "<base64>"
  },
  "oneTimePreKeys": [
    { "keyId": 1, "publicKey": "<base64>" },
    { "keyId": 2, "publicKey": "<base64>" }
  ]
}
```

**Response 200**:
```json
{
  "uploadedOneTimePreKeyCount": 100
}
```

---

### `POST /keys/prekeys/replenish`
Upload a fresh batch of one-time prekeys to replenish a depleted pool. Client should call this when the server-side pool drops below 20 keys.

**Request body**:
```json
{
  "deviceId": 1,
  "oneTimePreKeys": [
    { "keyId": 101, "publicKey": "<base64>" }
  ]
}
```

**Response 200**:
```json
{
  "poolSize": 120
}
```

---

## Common Error Shape

```json
{
  "error": {
    "code": "string",
    "message": "string"
  }
}
```
