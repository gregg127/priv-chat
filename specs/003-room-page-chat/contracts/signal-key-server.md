# Contract: Signal Key Server

**Feature**: `003-room-page-chat` | **Date**: 2026-04-01  
**Related**: `contracts/rest-api.md` (endpoint definitions), `data-model.md` (KeyBundle, OneTimePreKey entities)

This document describes the Signal Protocol key server protocol: when clients upload keys, how key bundles are fetched for X3DH, and the one-time prekey (OTP) replenishment lifecycle. Endpoint signatures are in `contracts/rest-api.md`.

---

## Key Bundle Upload

**Trigger**: After identity key pair is generated (T012 / T036), before the user can be invited to or participate in any room.

**Endpoint**: `POST /api/v1/keys/bundles`

**Request payload**:
```json
{
  "identityKey": "<base64 Curve25519 public identity key>",
  "signedPreKeyId": 1,
  "signedPreKey": "<base64 Curve25519 signed prekey>",
  "signedPreKeySignature": "<base64 Ed25519 signature of signedPreKey using identityKey>",
  "preKeys": [
    { "keyId": 1, "publicKey": "<base64 Curve25519 OTP>" },
    { "keyId": 2, "publicKey": "<base64 Curve25519 OTP>" }
  ]
}
```

**Constraints**:
- Client MUST upload at least 100 one-time prekeys on first registration.
- `signedPreKeySignature` MUST be verifiable against `identityKey` server-side.
- Server stores only public keys. Private keys MUST never be transmitted.

---

## Key Bundle Fetch (X3DH Initiator)

Used by the inviting owner to establish an X3DH session with a newly invited member before sending `SenderKeyDistributionMessage`.

**Endpoint**: `GET /api/v1/keys/bundles/:userId`

**Response payload**:
```json
{
  "userId": "<uuid>",
  "deviceId": 1,
  "identityKey": "<base64>",
  "signedPreKeyId": 1,
  "signedPreKey": "<base64>",
  "signedPreKeySignature": "<base64>",
  "preKey": {
    "keyId": 42,
    "publicKey": "<base64>"
  }
}
```

**OTP consumption**: Server deletes the returned `preKey` record immediately (single-use). If OTP pool is empty, the response omits the `preKey` field — X3DH proceeds without a one-time prekey (reduced security, acceptable per Signal specification).

**Privacy**: This endpoint is authenticated. Only room members or users being invited MAY fetch a bundle. Server MUST NOT allow arbitrary bundle fetching to prevent enumeration.

---

## Prekey Replenishment

Client MUST monitor its local OTP pool size. When fewer than 20 OTPs remain, it uploads a new batch.

**Endpoint**: `POST /api/v1/keys/prekeys/replenish`

**Request payload**:
```json
{
  "preKeys": [
    { "keyId": 101, "publicKey": "<base64>" },
    { "keyId": 102, "publicKey": "<base64>" }
  ]
}
```

**Server behaviour**: Appends new keys to the pool; does NOT replace existing unused keys. `keyId` values MUST be globally unique per user/device (client-managed monotonic counter).

---

## Signed Prekey Rotation

Not in scope for this version. The signed prekey uploaded at registration persists until a future rotation feature is implemented.

---

## Security Notes

- All key material in transit is protected by TLS 1.2+ (Constitution Security requirement).
- The server treats all stored keys as opaque bytes — it performs no cryptographic operations on private key material (zero-knowledge server).
- Key bundle fetch responses MUST be authenticated to prevent unauthenticated enumeration of public keys.
