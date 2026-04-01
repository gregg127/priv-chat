# Tasks: Room Page with Chat

**Input**: Design documents from `/specs/003-room-page-chat/`  
**Branch**: `003-room-page-chat`  
**Tech Stack**: TypeScript (frontend + backend), `@signalapp/libsignal-client` (WASM), WebSocket, PostgreSQL, IndexedDB, Vitest, Playwright

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US4)

---

## Phase 1: Setup

**Purpose**: Initialize project structure, tooling, and dependencies for both backend and frontend.

- [ ] T001 Create directory structure per plan.md: `backend/src/{models,services,api,ws}`, `backend/tests/{unit,integration,contract}`, `frontend/src/{pages,components,services,store}`, `frontend/tests/{unit,integration,e2e}`
- [ ] T002 Initialize backend TypeScript project: `backend/package.json`, `backend/tsconfig.json`, install `typescript`, `@types/node`, `ws`, `pg`, `@signalapp/libsignal-client`, `vitest`
- [ ] T003 [P] Initialize frontend TypeScript project: `frontend/package.json`, `frontend/tsconfig.json`, install `typescript`, `@signalapp/libsignal-client`, `vitest`, `@playwright/test`
- [ ] T004 [P] Configure ESLint + Prettier for backend in `backend/.eslintrc.json`, `backend/.prettierrc`
- [ ] T005 [P] Configure ESLint + Prettier for frontend in `frontend/.eslintrc.json`, `frontend/.prettierrc`

**Checkpoint**: Project structure ready; `npm install` succeeds in both `backend/` and `frontend/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure required before any user story can be implemented.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T006 Create PostgreSQL migration framework and initial schema migration covering all tables: `rooms`, `room_members`, `messages`, `key_bundles`, `one_time_prekeys` — per `data-model.md` — in `backend/src/db/migrations/001_initial_schema.sql`
- [ ] T007 Implement database client and migration runner in `backend/src/db/client.ts` and `backend/src/db/migrate.ts`
- [ ] T008 Implement JWT authentication middleware for REST routes in `backend/src/middleware/auth.ts` (validates Bearer token, attaches `userId` to request context)
- [ ] T009 Implement WebSocket server foundation: connection registry, auth-on-connect gate (first message must be `auth`), session map `clientId → {userId, roomIds[]}` in `backend/src/ws/server.ts`
- [ ] T010 [P] Implement privacy-safe structured logging service (metadata-only, fields per `research.md` Decision 8: no user IDs, no ciphertext, no keys) in `backend/src/lib/logger.ts`
- [ ] T011 [P] Implement token-bucket rate limiter: per-user (10 msg/s), per-room (100 msg/s), per-user-per-room (5 msg/s) in `backend/src/lib/rateLimiter.ts`
- [ ] T012 [P] Initialize `@signalapp/libsignal-client` WASM in frontend: load module, generate identity key pair on first use, store in IndexedDB in `frontend/src/services/signal/signalInit.ts`
- [ ] T013b Implement IndexedDB encryption layer: derive a 256-bit AES-GCM wrapping key from user credential via WebCrypto PBKDF2; expose `encrypt(data)` / `decrypt(data)` utilities — in `frontend/src/services/keystore/KeystoreEncryption.ts`. (Constitution Security: private keys MUST use platform-appropriate secure storage with encryption at rest.)
- [ ] T013 Implement IndexedDB keystore service: uses `KeystoreEncryption` for all reads/writes; stores `identity`, `signedPreKeys`, `preKeys`, `sessions`, `senderKeys`, `roomCursors` — schema per `data-model.md` Client-Side State — in `frontend/src/services/keystore/KeystoreService.ts` (depends on T013b)
- [ ] T014 [P] Implement frontend WebSocket service base: connect, auth handshake, reconnect with exponential backoff, event emitter for incoming frames in `frontend/src/services/websocket/WebSocketService.ts`

**Checkpoint**: DB migrations run successfully; WebSocket server boots and rejects unauthenticated connections; frontend keystore initializes without error; all IndexedDB key material is AES-GCM encrypted at rest.

---

## Phase 3: User Story 1 — View Room Details (Priority: P1) 🎯 MVP

**Goal**: A member navigates to the room page and sees the room name and live member list. Non-members are blocked.

**Independent Test**: Navigate to a room URL as an invited member → room name and all members displayed. Navigate as a non-member → access denied message shown.

### Tests for User Story 1 ⚠️ Write and confirm FAILING before any implementation

- [ ] T027 [US1] Write Playwright E2E test (must FAIL before T015 begins): member navigates to room, sees room name and member list within 2 seconds (SC-001); non-member gets access denied in `frontend/tests/e2e/room-details.spec.ts`

### Implementation for User Story 1

- [ ] T015 [P] [US1] Implement `Room` TypeScript model and DB query helpers (find by id, get with members) in `backend/src/models/Room.ts`
- [ ] T016 [P] [US1] Implement `RoomMember` TypeScript model and DB query helpers (list by room, get by userId+roomId, check membership) in `backend/src/models/RoomMember.ts`
- [ ] T017 [US1] Implement `RoomService`: `getRoomDetails(roomId, requestingUserId)` — returns room + member list, enforces membership check, 403 if not member — in `backend/src/services/room/RoomService.ts`
- [ ] T018 [US1] Implement `GET /api/v1/rooms/:roomId` REST endpoint: calls `RoomService.getRoomDetails`, returns room name + members array per `contracts/rest-api.md` in `backend/src/api/rooms.ts`
- [ ] T019 [US1] Implement WebSocket `subscribe` message handler: validate membership, add client to `roomChannels[roomId]`, send `subscribed` frame + `presence_update` for current online members in `backend/src/ws/handler.ts`
- [ ] T020 [US1] Implement `PresenceService`: heartbeat tracking (90s timeout), `member_joined` and `member_left` broadcast to room subscribers on connect/disconnect in `backend/src/services/websocket/PresenceService.ts`
- [ ] T021 [P] [US1] Implement `RoomHeader` component: displays room name and owner badge in `frontend/src/components/RoomHeader/RoomHeader.tsx`
- [ ] T022 [P] [US1] Implement `MemberList` component: renders list of members with online/offline status indicators in `frontend/src/components/MemberList/MemberList.tsx`
- [ ] T023 [US1] Implement room state store: holds `roomId`, `roomName`, `ownerId`, `members[]` with online status in `frontend/src/store/roomStore.ts`
- [ ] T024 [US1] Implement frontend `RoomService`: `fetchRoomDetails(roomId)` → `GET /api/v1/rooms/:roomId`, populates room store in `frontend/src/services/room/RoomService.ts`
- [ ] T025 [US1] Implement `RoomPage` layout: mounts `RoomHeader` + `MemberList`, calls `RoomService.fetchRoomDetails` on load, handles 403 with access-denied UI in `frontend/src/pages/room/RoomPage.tsx`
- [ ] T026 [US1] Wire WebSocket `subscribe` on room page mount: handle `member_joined`, `member_left`, `presence_update` frames → update room store in `frontend/src/services/websocket/WebSocketService.ts`

**Checkpoint**: US1 independently functional — room page shows name and live member list. Non-member access blocked at both REST and WebSocket layers.

---

## Phase 4: User Story 4 — Owner Invites Members (Priority: P2)

**Goal**: Room owner types a username and invites them; invited user immediately sees the room in their list and can join.

**Independent Test**: Owner invites a valid username → invited user's room list gains the room within 10 seconds. Inviting non-existent username → error shown. Non-owner cannot see invite UI.

### Prerequisite for User Story 4

- [ ] T027b [US4] **Blocks T041**: Amend feature `002-room-gateway` so its room list endpoint filters to only rooms where the requesting user has a matching `room_members` row. Without this the E2E test assertion "invited user sees room in their list" cannot pass. Deliver: update `002-room-gateway` spec and implementation to join `rooms` against `room_members` on `GET /api/v1/rooms`.

### Tests for User Story 4 ⚠️ Write and confirm FAILING before any implementation

- [ ] T041 [US4] **Depends on T027b**. Write Playwright E2E test (must FAIL before T028 begins): owner invites member by username; invited user sees room in their list; non-owner cannot see invite panel; invalid username shows error in `frontend/tests/e2e/invite.spec.ts`

### Implementation for User Story 4

- [ ] T028 [P] [US4] Implement `KeyBundle` and `OneTimePreKey` TypeScript models and DB query helpers (upsert bundle, get bundle with OTP, delete used OTP) in `backend/src/models/KeyBundle.ts` and `backend/src/models/OneTimePreKey.ts`
- [ ] T029 [US4] Implement `KeyServerService`: `getBundleForUser(userId)` (fetches public key bundle + consumes one OTP), `uploadBundle(userId, bundle)`, `replenishPrekeys(userId, keys)` in `backend/src/services/keyserver/KeyServerService.ts`
- [ ] T030 [P] [US4] Implement `GET /api/v1/keys/bundles/:userId` endpoint per `contracts/rest-api.md` and `contracts/signal-key-server.md` in `backend/src/api/keys.ts`
- [ ] T031 [US4] Implement `POST /api/v1/keys/bundles` and `POST /api/v1/keys/prekeys/replenish` endpoints per `contracts/rest-api.md` in `backend/src/api/keys.ts` (sequential after T030 — same file)
- [ ] T032 [US4] Implement `InviteService`: `inviteUser(roomId, ownerUserId, targetUsername)` — validates owner, resolves username to userId (generic 404 on miss to prevent enumeration), checks not already member, creates `RoomMember` with `join_seq = MAX(seq)` captured atomically in `backend/src/services/invite/InviteService.ts`
- [ ] T033 [US4] Implement `POST /api/v1/rooms/:roomId/invites` REST endpoint: calls `InviteService.inviteUser`, broadcasts `member_joined` to room WebSocket subscribers, returns `{ userId, roomId, joinedAt, joinSeq }` per `contracts/rest-api.md` in `backend/src/api/invites.ts`
- [ ] T034 [US4] Implement frontend `SignalService` X3DH + SenderKey distribution for new member: `fetchPreKeyBundle(userId)`, `initSessionWithMember(userId, bundle)`, `createSenderKeyDistributionMessage(roomId)`, `sealForMember(userId, distributionMsg)` in `frontend/src/services/signal/SignalService.ts`
- [ ] T035 [US4] Implement WebSocket `sender_key_distribution` send (owner sends after invite confirmed) and receive (new member processes, stores SenderKey) handlers in `frontend/src/services/websocket/WebSocketService.ts`
- [ ] T036 [US4] Implement frontend prekey bundle upload on session init (upload identity + signed prekey + 100 OTPs after identity key is generated) in `frontend/src/services/signal/signalInit.ts`
- [ ] T037 [US4] Implement OTP pool monitor: check pool size on app load and after each invite; call `POST /api/v1/keys/prekeys/replenish` when pool < 20 in `frontend/src/services/keystore/KeystoreService.ts`
- [ ] T038 [P] [US4] Implement `InvitePanel` component: username input field + submit button (owner only — hidden from non-owners), shows validation errors in `frontend/src/components/InvitePanel/InvitePanel.tsx`
- [ ] T039 [US4] Implement frontend `InviteService`: `sendInvite(roomId, username)` → `POST /api/v1/rooms/:roomId/invites`, then triggers `SignalService` session init + SenderKey distribution for new member in `frontend/src/services/room/InviteService.ts`
- [ ] T040 [US4] Mount `InvitePanel` in `RoomPage` (visible only when `userId === ownerId`); wire invite flow from form submit through `InviteService` in `frontend/src/pages/room/RoomPage.tsx`

**Checkpoint**: US4 independently functional — invite flow works end-to-end; invited user receives SenderKey and can decrypt room messages.

---

## Phase 5: User Story 2 — Send and Receive Messages (Priority: P2)

**Goal**: Members send E2E-encrypted messages; all room members receive them in real time. Server stores ciphertext only. Owner can delete individual messages. Empty/whitespace messages are rejected client-side and server-side.

**Independent Test**: Two members exchange messages within 1 second (SC-002); owner deletes a message and it disappears for all members; non-owner cannot see delete action; empty message submission is blocked with a validation error; non-member cannot access chat.

### Tests for User Story 2 ⚠️ Write and confirm FAILING before any implementation

- [ ] T052 [US2] Write unit test (must FAIL before T049 begins): Signal Protocol encrypt/decrypt round-trip using known Signal test vectors in `frontend/tests/unit/signal.test.ts`
- [ ] T053 [P] [US2] Write Playwright E2E test (must FAIL before T042 begins): (a) two users exchange messages; each sees the other's within 1 second (SC-002); (b) owner deletes a message — it disappears for all members; (c) non-owner has no delete action visible; (d) empty/whitespace message submission is blocked with a validation error; (e) non-member cannot send or view messages — in `frontend/tests/e2e/chat.spec.ts`

### Implementation for User Story 2

- [ ] T042 [P] [US2] Implement `Message` TypeScript model and DB query helpers: insert with atomic seq assignment (`MAX(seq)+1` per room), fetch by `roomId + seq range + join_seq boundary`, soft-delete by id in `backend/src/models/Message.ts`
- [ ] T043 [US2] Implement `MessageService`: `storeMessage(roomId, senderId, ciphertext, clientMessageId)` (deduplicates on `clientMessageId`, assigns seq, returns stored message), `deleteMessage(messageId, requestingUserId)` (owner-only, sets `deleted_at`, emits `message_deleted`) in `backend/src/services/message/MessageService.ts`
- [ ] T044 [US2] Implement WebSocket `message` handler: validate membership + rate limit + non-empty ciphertext (reject if payload is missing or zero-length, per FR-020) → call `MessageService.storeMessage` → send `message_ack` to sender → fan out `message_new` to all room subscribers in `backend/src/ws/handler.ts`
- [ ] T045 [US2] Implement `FanoutService`: broadcast a frame to all clients subscribed to a given `roomId` (iterates `roomChannels[roomId]`), handles dead connections gracefully in `backend/src/services/websocket/FanoutService.ts`
- [ ] T046 [US2] Implement `GET /api/v1/rooms/:roomId/messages` REST endpoint: paginated by `before_seq`, enforces `join_seq` boundary, returns ciphertext array per `contracts/rest-api.md` in `backend/src/api/messages.ts`
- [ ] T047 [P] [US2] Implement `ChatArea` component: scrollable message list (ordered by seq), message input box with send button disabled when input is empty or whitespace (FR-020 client validation + show validation hint on submit attempt); optimistic send (confirm on `message_ack`); each message displays sender's `displayName` and formatted `serverTimestamp` (FR-007); owner-only delete icon per message — clicking sends `delete_message` WS frame and removes from local store (FR-017); non-owner sees no delete icon; render empty-state ("No messages yet") when message store is empty (FR-011) in `frontend/src/components/ChatArea/ChatArea.tsx`
- [ ] T048 [US2] Implement message store: holds messages ordered by `seq`, deduplicates on `clientMessageId`, handles `message_deleted` removal in `frontend/src/store/messageStore.ts`
- [ ] T049 [US2] Implement frontend `SignalService` SenderKey encrypt/decrypt: `encryptMessage(roomId, plaintext)` → base64 `SenderKeyMessage`, `decryptMessage(roomId, senderId, ciphertext)` → plaintext in `frontend/src/services/signal/SignalService.ts`
- [ ] T050 [US2] Implement WebSocket `message` send (encrypt → send frame), `message_ack` handler (update store with seq + serverTimestamp + senderDisplayName), `message_new` handler (decrypt → add to store; payload includes `senderDisplayName` resolved server-side from User entity), `message_deleted` handler (remove from store), and `delete_message` send (owner clicks delete → send `{ type: "delete_message", messageId }` frame to server — triggers `MessageService.deleteMessage`) in `frontend/src/services/websocket/WebSocketService.ts`
- [ ] T051 [US2] Mount `ChatArea` in `RoomPage`; wire send action through `SignalService.encryptMessage` → WebSocket `message` frame in `frontend/src/pages/room/RoomPage.tsx`

**Checkpoint**: US2 independently functional — real-time E2E-encrypted messaging works within 1 second (SC-002); owner can delete messages; non-owner cannot; empty messages blocked; server stores only ciphertext; non-members blocked.

---

## Phase 6: User Story 3 — View Chat History (Priority: P3)

**Goal**: Members loading the room page see prior messages from their invite date onward. Reconnecting members receive all missed messages automatically.

**Independent Test**: Send 10 messages, reload room page — all 10 messages appear decrypted in order. Disconnect, have another member send 3 messages, reconnect — 3 missed messages delivered automatically.

### Tests for User Story 3 ⚠️ Write and confirm FAILING before any implementation

- [ ] T059 [US3] Write Playwright E2E test (must FAIL before T054 begins): member reconnects after disconnect; receives all missed messages in correct seq order in `frontend/tests/e2e/reconnect.spec.ts`
- [ ] T060 [P] [US3] Write Vitest integration test (must FAIL before T057 begins): history boundary enforced — new member cannot retrieve messages with seq < join_seq via REST or WebSocket catch-up in `backend/tests/integration/history-boundary.test.ts`

### Implementation for User Story 3

- [ ] T054 [US3] Implement frontend `HistoryService`: on room page open, fetch `GET /api/v1/rooms/:roomId/messages` (paged from `joinSeq`), decrypt each message via `SignalService`, populate message store in `frontend/src/services/room/HistoryService.ts`
- [ ] T055 [US3] Update `ChatArea` to scroll to bottom on initial history load; support upward scroll-pagination (fetch older messages via `before_seq`) in `frontend/src/components/ChatArea/ChatArea.tsx`
- [ ] T056 [US3] Persist `lastSeenSeq` and `joinSeq` per room in IndexedDB `roomCursors` store; update `lastSeenSeq` as messages are received in `frontend/src/services/keystore/KeystoreService.ts`
- [ ] T057 [US3] Implement WebSocket catch-up in `backend/src/ws/handler.ts`: on `subscribe` with `lastSeenSeq`, query `MessageService` for missed messages, send `catchup_batch` frames (max 50 per batch) then `catchup_complete` frame per `contracts/websocket.md`
- [ ] T058 [US3] Implement frontend reconnect catch-up: on WebSocket reconnect, re-subscribe with `lastSeenSeq` from IndexedDB; process `catchup_batch` (decrypt + insert into store in order) and `catchup_complete` in `frontend/src/services/websocket/WebSocketService.ts`

**Checkpoint**: US3 functional — history loads on page open; reconnect delivers missed messages; join_seq boundary enforced for new members.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Edge cases, security hardening, observability, and lifecycle events that span multiple user stories.

- [ ] T061 [P] Implement `OwnershipService`: on user account deletion, transfer room ownership to member with `MIN(joined_at)` (atomic with account delete); broadcast `ownership_transferred` to room; delete room if no other members remain in `backend/src/services/room/OwnershipService.ts`
- [ ] T062 [P] Handle deleted-user membership: on account deletion, remove from active member list, broadcast `member_left`; existing messages retain `sender_id` attribution (displayed as "deleted user") in `backend/src/services/room/RoomService.ts`
- [ ] T063 [P] Add privacy-safe observability logging to all backend services: connection events, message throughput (aggregate), delivery latency p95, security events with `sha256(userId)[0:12]` — per `research.md` Decision 8 — across `backend/src/services/`
- [ ] T064 [P] Add backend integration tests for security boundaries: non-member `subscribe` rejected, non-member `GET /rooms/:roomId` returns 403, non-owner `POST /invites` returns 403 in `backend/tests/integration/auth.test.ts`
- [ ] T065 [P] Add `unsubscribe` WebSocket handler: remove client from `roomChannels[roomId]`, broadcast `member_left` in `backend/src/ws/handler.ts`
- [ ] T066 Run quickstart.md validation scenarios end-to-end and resolve any failures

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — **BLOCKS all user story phases**
- **Phase 3 (US1)**: Depends on Phase 2 — can start as soon as Foundation is done
- **Phase 4 (US4)**: Depends on Phase 2 — can start in parallel with Phase 3; T027b (feature 002 amendment) must complete before T041 can pass
- **Phase 5 (US2)**: Depends on Phase 2 + Phase 4 (requires SenderKey distribution to be in place for full E2E crypto flow)
- **Phase 6 (US3)**: Depends on Phase 5 (history requires messages to exist)
- **Phase 7 (Polish)**: Depends on Phases 3–6 as applicable per task

### User Story Dependencies

```
Phase 1 (Setup)
    └── Phase 2 (Foundational)
            ├── Phase 3 (US1: Room Details)   ← independent, start immediately after Phase 2
            └── Phase 4 (US4: Invite)         ← independent, can run in parallel with US1
                    └── Phase 5 (US2: Chat)   ← needs SenderKey from US4
                            └── Phase 6 (US3: History) ← needs messages from US2
```

### Within Each Phase

- Backend model tasks [P] → run in parallel with each other
- Backend service task → depends on its models
- REST endpoint task → depends on its service
- WebSocket handler → depends on services
- Frontend component [P] → parallel with other components
- Frontend service → depends on backend contract + frontend keystore
- Frontend page → depends on components + services
- E2E test → depends on all implementation tasks in the phase

---

## Parallel Opportunities

### Phase 2 (Foundational) — after T006/T007 complete:

```
T008 (WS server)       ← in parallel with →   T010 (logger)
T009 (frontend WS)     ← in parallel with →   T011 (rate limiter)
T012 (Signal WASM)     ← in parallel with →   T013 (keystore)
T014 (frontend WS base) runs after T013
```

### Phase 3 (US1):

```
T015 (Room model) + T016 (RoomMember model) ← parallel
T021 (RoomHeader) + T022 (MemberList)       ← parallel
T017 → T018 → T019 (backend sequential)
T023 → T024 → T025 → T026 (frontend sequential)
```

### Phase 4 (US4):

```
T028 (KeyBundle/OTP models) ← parallel with → T038 (InvitePanel component)
T030 (key server GET) ← parallel with T028/T038
T031 (key server POST/replenish) ← sequential after T030 (same file: keys.ts)
T032 (InviteService) → T033 (invite endpoint) ← sequential
T034 (SignalService X3DH) → T035 (WS distribution) ← sequential
```

### Phase 5 (US2):

```
T042 (Message model) ← parallel with → T047 (ChatArea component)
T043 (MessageService) → T044 (WS handler) → T045 (REST endpoint)
T049 (Signal encrypt/decrypt) → T050 (WS handlers) ← sequential
```

---

## Implementation Strategy

### MVP (User Story 1 only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: US1 (room details + member list)
4. **STOP and validate**: Room page shows name and live members. Non-member blocked.

### Incremental Delivery

1. Phase 1 + 2 → Foundation ready
2. Phase 3 → **Demo**: Room page with member list (MVP)
3. Phase 4 → **Demo**: Owner can invite members via username
4. Phase 5 → **Demo**: Real-time E2E-encrypted chat
5. Phase 6 → **Demo**: Full persistent history + reconnect catch-up
6. Phase 7 → Production-ready hardening

### Parallel Team Strategy

After Phase 2 completes:
- **Dev A**: Phase 3 (US1 — room details)
- **Dev B**: Phase 4 (US4 — invite flow + key server)
- **Dev C**: Phase 4 backend (key server) while Dev B handles invite frontend
- Phase 5 starts when Dev A + B converge on US1 + US4 completion
