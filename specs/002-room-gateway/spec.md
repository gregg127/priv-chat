# Feature Specification: Room Gateway

**Feature Branch**: `002-room-gateway`
**Created**: 2026-03-31
**Status**: Draft
**Input**: User description: "room gateway - main screen that logged in users sees. On this screen user sees a list of public rooms (each one has a join button) and a button to create a room with some default values (like room name created from username and number of setup room)"

## Clarifications

### Session 2026-03-31

- Q: Are rooms permanent or ephemeral once created? → A: Rooms are permanent — they remain in the list forever regardless of occupancy.
- Q: Is room metadata (names, creator username) considered sensitive/encrypted? → A: Room metadata is plaintext — visible to all authenticated members, stored as-is on the server. This is an explicit, documented exception to the zero-knowledge server principle; the planning threat model must record this decision.
- Q: What details does each room card display in the list? → A: Name, creator username, creation timestamp, and current occupant count.
- Q: Is room creation rate-limited or capped? → A: Hard cap — maximum 10 rooms per user total.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browsing and Joining Public Rooms (Priority: P1)

A logged-in user lands on the Room Gateway — the first screen they see after
passing the network entry gate. They see a list of all currently available
public rooms. Each room shows its name and a "Join" button. The user clicks
"Join" on a room they are interested in and enters that room.

**Why this priority**: This is the core purpose of the gateway. Without it,
logged-in users have nowhere to go and cannot participate in the chat network.
A browsable list with join actions is the minimum viable post-login experience.

**Independent Test**: After logging in via the entry gate (feature 001), verify
that at least one public room appears in the list. Click "Join" and confirm the
user enters the chosen room. Deliverable value: a user can discover and enter
a conversation.

**Acceptance Scenarios**:

1. **Given** a user has a valid session and at least one public room exists,
   **When** they navigate to the Room Gateway, **Then** they see a list
   containing all public rooms, each displaying the room name and a "Join"
   button.

2. **Given** the Room Gateway is visible with multiple rooms, **When** the user
   clicks "Join" on a specific room, **Then** the "Join" button is available
   (navigation into the room's chat view is deferred to feature 003).

3. **Given** the Room Gateway is visible, **When** a new room is created by
   another user, **Then** that room appears in the list within 10 seconds
   (via polling) without requiring a page refresh.

4. **Given** the user has no valid session, **When** they navigate to the Room
   Gateway URL directly, **Then** they are redirected to the network entry gate.

---

### User Story 2 - Creating a Room with Default Values (Priority: P2)

A logged-in user wants to start a new conversation. They click the "Create
Room" button on the Room Gateway. A new public room is created immediately with
a default name derived from the user's username and a sequential room counter
(e.g., "alice-room-3" for Alice's third created room). The new room appears in
the list and the user is taken into it (chat navigation deferred to feature 003).

**Why this priority**: Room creation is the supply side of the gateway. Without
it, users can only consume existing rooms. It is prioritised below P1 because
the gateway is still useful for joining if room creation is not yet available.

**Independent Test**: From the Room Gateway, click "Create Room". Verify a new
room appears in the list with a name following the `{username}-room-{n}` pattern.
(Navigation into the room is deferred to feature 003.) Deliverable value: a
user can start a new conversation without any configuration.

**Acceptance Scenarios**:

1. **Given** a logged-in user is on the Room Gateway, **When** they click
   "Create Room", **Then** a new public room is created with the default name
   `{username}-room-{n}` where `n` is the next sequential number for rooms
   created by that user, and the new room appears at the top of the room list.
   (Navigation into the room is deferred to feature 003.)

2. **Given** Alice has previously created 2 rooms, **When** she clicks "Create
   Room" again, **Then** the new room is named `alice-room-3`.

3. **Given** a new room has just been created, **When** another logged-in user
   views the Room Gateway, **Then** they can see the new room in the list and
   join it.

---

### User Story 3 - Empty State (Priority: P3)

A logged-in user arrives at the Room Gateway when no public rooms have been
created yet. Instead of a blank or broken screen, they see a friendly empty
state message and a prominent "Create Room" button to get started.

**Why this priority**: Necessary for a complete and polished user experience,
especially for the first user to join a fresh portal. Lower priority because it
is a degenerate case once the portal has activity.

**Independent Test**: Access the Room Gateway on a portal with no rooms. Verify
a meaningful empty-state message is displayed and the "Create Room" button is
visible and functional.

**Acceptance Scenarios**:

1. **Given** there are no public rooms, **When** a logged-in user opens the
   Room Gateway, **Then** an empty-state message ("No rooms yet — create one
   to get started" or equivalent) is shown alongside the "Create Room" button.

2. **Given** the empty state is displayed, **When** the user clicks "Create
   Room", **Then** behavior follows User Story 2.

---

### User Story 4 - Room Management: Rename and Delete (Priority: P4)

A logged-in user who created a room wants to manage it. From the Room Gateway,
the creator of a room can rename it or permanently delete it. These actions are
only available to the room's creator — other users cannot modify or remove
rooms they did not create.

**Why this priority**: Gives creators ownership and control over their rooms.
Lower priority than browsing and creation; the gateway is fully usable without
it. Deletion also frees a slot toward the 10-room cap, enabling the creator to
create new rooms.

**Independent Test**: As the creator of a room, click "Rename" on a room card,
enter a new name, confirm, and verify the room list reflects the new name.
Then click "Delete" on a room, confirm the dialog, and verify the room
disappears from the list and the creator's active room count decreases by one.
Attempt the same actions as a non-creator and verify they are not available /
return an error.

**Acceptance Scenarios**:

1. **Given** a logged-in user is the creator of a room, **When** they click
   "Rename" on that room's card and submit a new valid name, **Then** the room
   is renamed and the new name appears in the room list immediately.

2. **Given** a new name is already taken by another room, **When** the creator
   submits the rename, **Then** an error is shown ("Room name already taken")
   and the room retains its original name.

3. **Given** a logged-in user is the creator of a room, **When** they click
   "Delete" and confirm, **Then** the room is permanently removed from the
   list and the creator's active room slot is freed (enabling a new room to be
   created if they were at the 10-room cap).

4. **Given** a logged-in user is NOT the creator of a room, **When** they
   attempt to rename or delete via the API, **Then** a 403 Forbidden response
   is returned and an `UNAUTHORIZED_ATTEMPT` event is written to the audit log.

5. **Given** rename and delete controls are rendered, **Then** they are only
   visible / enabled on rooms where `creatorUsername` matches the current user's
   JWT `sub` claim.

---

### Edge Cases

- What happens when the room list is very long (e.g., hundreds of rooms)?
  → The list is scrollable; all rooms are accessible without pagination for v1.
- What happens if two users click "Create Room" at the same instant?
  → Both rooms are created independently; each user ends up in their own new room.
- What happens if the user's network connection drops while on the Room Gateway?
  → The room list freezes in its last known state; an offline/reconnecting
  indicator is shown, and the list refreshes automatically on reconnect.
- What happens if the default room name would conflict with an existing room name?
  → The sequential counter continues incrementing until a unique name is found.
- What happens when a user has already created 10 rooms and clicks "Create Room"?
  → The "Create Room" button is disabled; a message informs the user they have
  reached the 10-room limit. The button re-enables if the user deletes a room,
  freeing an active room slot (FR-014).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Room Gateway MUST be the screen presented to a user
  immediately after a successful login via the network entry gate.
- **FR-002**: The Room Gateway MUST display a list of all currently available
  public rooms. Each room card MUST show: room name, creator username, creation
  timestamp, current occupant count, and a "Join" button.
- **FR-003**: The room list MUST update within 10 seconds (via polling) when
  rooms are created or removed by other users, without requiring a page refresh.
- **FR-004**: The Room Gateway MUST display a "Join" button on each room card.
  Navigation into the room's chat view is deferred to feature 003 (Chat Room).
- **FR-005**: The Room Gateway MUST provide a single "Create Room" button.
- **FR-006**: Clicking "Create Room" MUST create a new public room with a
  default name following the pattern `{username}-room-{n}`, where `n` is the
  next available sequential number for rooms created by that user (starting
  at 1). The API additionally accepts an optional custom `name` field for
  future extensibility; the v1 UI does not expose this option.
- **FR-007**: After creating a room, the new room MUST appear at the top of the
  room list immediately. Navigation into the newly created room is deferred to
  feature 003 (Chat Room).
- **FR-008**: When no rooms exist, the Room Gateway MUST display an empty-state
  message and keep the "Create Room" button prominently visible.
- **FR-009**: Access to the Room Gateway MUST be restricted to users with a
  valid session; unauthenticated requests MUST be redirected to the entry gate.
- **FR-012**: A user MUST NOT be able to create more than 10 rooms in total.
  Once the limit is reached, the "Create Room" button MUST be disabled and a
  message MUST inform the user they have reached their room creation limit. This is an explicit, documented
  exception to the zero-knowledge server principle; all authenticated portal
  members can read this metadata. The planning threat model MUST record this
  decision and assess residual risk.
- **FR-013**: The creator of a room MUST be able to rename it by submitting a
  new name. The new name MUST be globally unique (max 100 characters). A 409
  error MUST be returned if the name is already taken. Only the creator
  (identified by JWT `sub` claim) may rename a room; any other user MUST
  receive 403.
- **FR-014**: The creator of a room MUST be able to permanently delete it.
  Deleting a room MUST decrement the creator's `active_rooms_count` by one,
  freeing a creation slot. Only the creator may delete; any other user MUST
  receive 403. All mutation failures by non-creators MUST write an
  `UNAUTHORIZED_ATTEMPT` entry to `room_audit_log`.
- **FR-015**: Rename and delete controls in the UI MUST only be visible and
  enabled on room cards where the `creatorUsername` matches the authenticated
  user's username.

### Key Entities

- **Room**: A named conversation space. Attributes: unique identifier, display
  name, creator username, creation timestamp, current occupant count,
  public/private status (all rooms are public in v1). Rooms are permanent —
  they persist indefinitely and are never automatically removed regardless of
  occupancy.
- **Session**: Carries the logged-in user's identity (username) as established
  by the network entry gate (feature 001).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A logged-in user can find and join a public room in under
  30 seconds from the moment the Room Gateway loads.
- **SC-002**: A logged-in user can create a new room in under 5 seconds
  (one click, no configuration required).
- **SC-003**: The room list reflects newly created rooms within 10 seconds (polling interval intentionally set to 10 s to avoid interval-restart memory leak)
  of creation across all connected clients.
- **SC-004**: 100% of unauthenticated navigation attempts to the Room Gateway
  result in a redirect to the entry gate.
- **SC-005**: The Room Gateway loads and is interactive within 3 seconds on
  a standard broadband connection.

## Assumptions

- Users are already authenticated via feature 001 (Network Entry Gate); this
  feature relies on the session established there.
- All rooms created via this screen are public; private/invite-only rooms are
  out of scope for v1.
- Room names generated from usernames use the pattern `{username}-room-{n}`
  where `n` starts at 1 and increments per user (not globally).
- The room list is a flat, unordered (or reverse-chronological) list; search,
  filtering, and sorting are out of scope for v1.
- Rooms are permanent once created; they are never automatically removed and
  always remain visible in the Room Gateway list regardless of how many users
  are currently inside.
- Room deletion and renaming are in scope — creator-only actions (FR-013, FR-014, FR-015).
- Each user may create a maximum of 10 rooms total. The "Create Room" button is
  disabled once this limit is reached.
- Users with a valid session are already identified by their chosen username
  from the entry gate; no additional profile data is required.
