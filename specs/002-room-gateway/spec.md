# Feature Specification: Room Gateway

**Feature Branch**: `002-room-gateway`
**Created**: 2026-03-31
**Status**: Draft
**Input**: User description: "room gateway - main screen that logged in users sees. On this screen user sees a list of public rooms (each one has a join button) and a button to create a room with some default values (like room name created from username and number of setup room)"

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
   clicks "Join" on a specific room, **Then** they are taken into that room's
   chat view.

3. **Given** the Room Gateway is visible, **When** a new room is created by
   another user (in real time), **Then** that room appears in the list without
   requiring a page refresh.

4. **Given** the user has no valid session, **When** they navigate to the Room
   Gateway URL directly, **Then** they are redirected to the network entry gate.

---

### User Story 2 - Creating a Room with Default Values (Priority: P2)

A logged-in user wants to start a new conversation. They click the "Create
Room" button on the Room Gateway. A new public room is created immediately with
a default name derived from the user's username and a sequential room counter
(e.g., "alice-room-3" for Alice's third created room). The new room appears in
the list and the user is taken into it.

**Why this priority**: Room creation is the supply side of the gateway. Without
it, users can only consume existing rooms. It is prioritised below P1 because
the gateway is still useful for joining if room creation is not yet available.

**Independent Test**: From the Room Gateway, click "Create Room". Verify a new
room appears in the list with a name following the `{username}-room-{n}` pattern
and that the user is immediately placed inside that room. Deliverable value: a
user can start a new conversation without any configuration.

**Acceptance Scenarios**:

1. **Given** a logged-in user is on the Room Gateway, **When** they click
   "Create Room", **Then** a new public room is created with the default name
   `{username}-room-{n}` where `n` is the next sequential number for rooms
   created by that user, and the user is taken into the new room.

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

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Room Gateway MUST be the screen presented to a user
  immediately after a successful login via the network entry gate.
- **FR-002**: The Room Gateway MUST display a list of all currently available
  public rooms, each showing at minimum the room name and a "Join" button.
- **FR-003**: The room list MUST update in real time when rooms are created or
  removed by other users, without requiring a page refresh.
- **FR-004**: Clicking the "Join" button on a room MUST navigate the user into
  that room's chat view.
- **FR-005**: The Room Gateway MUST provide a single "Create Room" button.
- **FR-006**: Clicking "Create Room" MUST create a new public room with a
  default name following the pattern `{username}-room-{n}`, where `n` is the
  next available sequential number for rooms created by that user (starting
  at 1).
- **FR-007**: After creating a room, the user MUST be automatically navigated
  into the newly created room.
- **FR-008**: When no rooms exist, the Room Gateway MUST display an empty-state
  message and keep the "Create Room" button prominently visible.
- **FR-009**: Access to the Room Gateway MUST be restricted to users with a
  valid session; unauthenticated requests MUST be redirected to the entry gate.
- **FR-010**: The room list MUST be scrollable to accommodate any number of rooms.

### Key Entities

- **Room**: A named conversation space. Attributes: unique identifier, display
  name, creator username, creation timestamp, public/private status (all rooms
  are public in v1).
- **Session**: Carries the logged-in user's identity (username) as established
  by the network entry gate (feature 001).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A logged-in user can find and join a public room in under
  30 seconds from the moment the Room Gateway loads.
- **SC-002**: A logged-in user can create a new room in under 5 seconds
  (one click, no configuration required).
- **SC-003**: The room list reflects newly created rooms within 2 seconds
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
- Room deletion and renaming are out of scope for this feature.
- The maximum number of concurrent rooms is not bounded in v1.
- Users with a valid session are already identified by their chosen username
  from the entry gate; no additional profile data is required.
