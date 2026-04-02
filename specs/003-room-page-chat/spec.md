# Feature Specification: Room Page with Chat

**Feature Branch**: `003-room-page-chat`  
**Created**: 2026-04-01  
**Status**: Draft  
**Input**: User description: "room page that shows room details like name, participants and contains chat that people inside room can use (send private messages)"

## Clarifications

### Session 2026-04-01

- Rooms are **invite-only** — the room owner explicitly invites users by typing their username; there is no open join flow.
- Users see **only the rooms they have been invited to** on the room list (Room Gateway, feature 002). This supersedes the public-rooms model described in `002-room-gateway`, which must be updated accordingly.
- The **room owner** manages the invite list from within the room page.
- Q: What is the message privacy model? → A: Messages are end-to-end encrypted — server stores and relays ciphertext only; the server cannot read message content.
- Q: How long are messages retained? → A: Persistent indefinitely — messages are retained until the room owner explicitly deletes them.
- Q: What happens to messages sent during a member's disconnection? → A: All messages sent during the disconnection are automatically delivered when the member reconnects.
- Q: Can newly invited members access message history from before they joined? → A: No — new members see only messages sent after they were invited; pre-invite history is not accessible to them.
- Q: What happens to the room when the owner's account is deleted? → A: Ownership automatically transfers to the longest-standing member; the room and all its messages remain accessible.
- Q: How does the participant list behave when the same user connects from multiple devices? → A: One entry per user — the user appears once in the member list, shown as active if any of their devices is currently connected; device count is not exposed to other members.
- Q: Can the room owner delete the entire room? → A: Out of scope for this version — room deletion is not supported; rooms persist indefinitely.

> ⚠️ **Impact on feature 002 (`002-room-gateway`)**: That spec describes "public rooms" visible to all users. The invite-only model changes this — the room gateway should show only rooms the user has been invited to, not a global public list. Feature 002 must be re-specified or amended before implementation.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View Room Details (Priority: P1)

A participant navigates to a room page and sees the room's name and the list of people who are members of the room. This gives users the essential context of who they are communicating with before or while chatting.

**Why this priority**: This is the foundational display layer. Without knowing room context and who else is present, all other functionality is meaningless. It is the minimum viable room page.

**Independent Test**: Can be fully tested by navigating to a room URL and verifying that the room name and participant list render correctly, delivering immediate value as a "room presence" feature.

**Acceptance Scenarios**:

1. **Given** a user is a member of a room, **When** they navigate to the room page, **Then** they see the room's name prominently displayed
2. **Given** a user is on the room page, **When** the page loads, **Then** they see a list of all current room members including themselves
3. **Given** a participant joins or leaves the room, **When** the user is viewing the room page, **Then** the participant list updates to reflect the change without requiring a page reload
4. **Given** a user is not a member of a room, **When** they attempt to navigate to that room's page directly, **Then** they are denied access and see an appropriate message
5. **Given** a user is a member of a room, **When** the room page finishes loading under normal network conditions, **Then** the room name and full member list are both visible within 2 seconds (SC-001)

---

### User Story 2 - Send and Receive Messages in Room (Priority: P2)

A participant inside a room can type and send a message visible only to other people in that room. Messages appear in a chat area in chronological order, enabling real-time private group conversation among room members.

**Why this priority**: This is the core communication feature of the room. Sending and receiving messages is the primary reason users enter a room.

**Independent Test**: Can be fully tested by having two users in the same room exchange messages and verifying both receive them, while a user outside the room cannot see the conversation.

**Acceptance Scenarios**:

1. **Given** a user is inside a room, **When** they type a message and submit it, **Then** the message appears in the chat area for all room participants
2. **Given** a user is inside a room, **When** another participant sends a message, **Then** the new message appears in the chat area without requiring a page reload
3. **Given** a user is not a member of a room, **When** they attempt to access the room's chat, **Then** they are denied access and see an appropriate message
4. **Given** a user sends a message, **When** it is delivered, **Then** the message shows the sender's name and the time it was sent
5. **Given** a user sends a message under normal network conditions, **When** it is delivered, **Then** it appears in the chat for all room members within 1 second (SC-002)
6. **Given** a user is a member of a room, **When** they select the delete action on one of their own messages, **Then** that message is immediately removed from the chat view for all room members and cannot be retrieved (FR-017)
7. **Given** a user views messages they did not send, **When** they look at those messages, **Then** no delete action is visible or accessible to them (FR-017)
8. **Given** the room owner views any message in the chat, **When** they look at messages sent by others, **Then** no delete action is visible; the owner may only delete their own messages (FR-017)
8. **Given** a user is composing a message, **When** they attempt to submit a message that is empty or contains only whitespace characters, **Then** the system prevents submission and displays a validation error; the message is not sent (FR-020)

---

### User Story 3 - View Chat History (Priority: P3)

When a room participant opens the room page, they can scroll through previous messages sent in that room, providing conversational context before contributing.

**Why this priority**: History is important for context but not blocking — users can still interact even if only real-time messages are shown. This is a quality-of-life enhancement.

**Independent Test**: Can be fully tested by sending messages, leaving the room, re-entering, and verifying prior messages are still visible in order.

**Acceptance Scenarios**:

1. **Given** messages have been sent in a room, **When** a member opens the room page, **Then** they see the previous messages in chronological order
2. **Given** a room has many messages, **When** a user opens the room page, **Then** the most recent messages are visible without scrolling and older messages can be scrolled to
3. **Given** a room has no prior messages, **When** a user opens the room page, **Then** an empty state is shown indicating no messages yet

---

### User Story 4 - Owner Invites Members to Room (Priority: P2)

The room owner can invite other users to join the room by typing their username into an invite field on the room page. Once invited, the invited user gains access to the room and it appears in their room list on the Room Gateway.

**Why this priority**: Rooms are invite-only — without the ability for the owner to invite people, no one else can ever join and the chat is unusable. This is tied in priority to messaging since both are core to room utility, but invite must logically exist for messaging to occur.

**Independent Test**: Can be fully tested by the room owner typing a valid username into the invite field, submitting it, and verifying the invited user now sees the room in their room list and can navigate to it.

**Acceptance Scenarios**:

1. **Given** a user is the owner of a room, **When** they type a valid username into the invite field and confirm, **Then** that user is added to the room's member list and the room appears in the invited user's room list
2. **Given** a user is the owner of a room, **When** they type a username that does not exist in the system, **Then** an error message is shown and no invite is sent
3. **Given** a user is the owner of a room, **When** they type a username that is already a member of the room, **Then** an appropriate message is shown indicating the user is already a member
4. **Given** a user is not the owner of a room, **When** they view the room page, **Then** no invite control is visible or accessible to them
5. **Given** a room owner sends an invite, **When** the invited user next views the Room Gateway, **Then** the newly accessible room is listed

---

### Edge Cases

- What happens when a user tries to access a room they are not a member of?
- How does the participant list behave when the same user connects from multiple devices? → The user appears once in the list, shown as active if any device is connected; device count is not exposed to other members (see FR-003)
- What happens when a user sends an empty message? → The system prevents submission and displays a validation error; empty or whitespace-only messages are rejected before sending (see FR-020)
- How does the chat handle a very long message that exceeds typical display width?
- What happens when a room has only one participant (the owner themselves, with no invited members yet)?
- How does the system behave when the connection is temporarily lost and then restored? → Missed messages are automatically delivered in order on reconnect (see FR-018)
- What happens if the room owner invites a user who then gets their account deleted? → That member is removed from the active member list; existing messages they sent remain visible (as ciphertext attributed to a deleted user)
- Can the room owner remove a previously invited member? → Out of scope for this version (see Assumptions)
- What happens to the room when the owner's account is deleted? → Ownership automatically transfers to the longest-standing member (see FR-019)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display the room's name on the room page
- **FR-002**: System MUST display a list of all members of the room
- **FR-003**: System MUST update the member list in real time when members become active or inactive; each user MUST appear at most once in the list regardless of how many devices they are connected from; a user is shown as active if any of their devices is currently connected; device count MUST NOT be exposed to other room members
- **FR-004**: Users MUST be able to type and submit a message from the room page
- **FR-005**: Submitted messages MUST be delivered to all current room members in real time
- **FR-006**: Messages MUST be end-to-end encrypted — the server stores and relays ciphertext only and MUST NOT be able to read message content; only room members with the appropriate keys can decrypt messages
- **FR-007**: Each message MUST display the sender's name and the time it was sent
- **FR-008**: The chat area MUST display messages in chronological order
- **FR-009**: System MUST load and display the full persistent message history for a member starting from the point they were invited; messages sent before a member's invite date MUST NOT be accessible to them; messages are retained indefinitely until deleted by the room owner
- **FR-017**: Each member MUST be able to delete their own messages via a delete action visible exclusively to the sender on each of their own messages; deleted messages MUST be immediately removed from the chat view for all members via WebSocket broadcast and MUST NOT be retrievable. Room owners do NOT have additional delete authority over other members' messages — this is intentional to protect privacy of contributions.
- **FR-018**: When a member reconnects after a disconnection, the system MUST automatically deliver all messages they missed during the gap, in chronological order
- **FR-019**: When the room owner's account is deleted, ownership MUST automatically transfer to the longest-standing member (earliest invite timestamp); the room and all messages MUST remain accessible
- **FR-010**: System MUST prevent unauthenticated or non-member users from viewing or sending messages
- **FR-011**: System MUST show an appropriate empty state when no messages exist in the room
- **FR-012**: The room owner MUST be able to invite a user to the room by entering that user's username
- **FR-013**: System MUST validate that the invited username exists before completing the invite
- **FR-014**: System MUST show an error if the owner invites a username that does not exist or is already a member
- **FR-015**: Only the room owner MUST have access to the invite functionality; regular members cannot invite others
- **FR-016**: Once invited, the room MUST appear in the newly invited user's room list on the Room Gateway
- **FR-020**: The system MUST reject submission of empty or whitespace-only messages; a clear validation error MUST be displayed and the send action MUST be disabled or blocked when the message input is empty or whitespace-only

### Key Entities

- **Room**: A named invite-only private space; has a name, an owner, and a membership list; only visible to invited members; ownership transfers to the longest-standing member if the owner's account is deleted
- **Room Owner**: The user who created the room (or inherited ownership via transfer); has exclusive ability to invite new members and delete messages; at most one owner per room at any time
- **Member**: A user who has been invited to a room; can view and send messages within that room
- **Invite**: An action by the room owner that grants a specific user access to the room by username lookup; records the invite timestamp which acts as the history visibility boundary for that member
- **Message**: A text communication sent by a member within a room; end-to-end encrypted — stored and relayed as ciphertext; only room members can decrypt; records sender identity, ciphertext content, and timestamp; persisted indefinitely until explicitly deleted by the room owner
- **Room Session**: Represents a user's active presence in a room; a user is considered active if at least one of their connected devices is subscribed to the room; each user appears at most once in the participant list regardless of device count

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Room name and member list are visible within 2 seconds of opening the room page under normal network conditions
- **SC-002**: Messages sent by one member appear for all other room members within 1 second under normal network conditions
- **SC-003**: The member list reflects member activity changes within 2 seconds
- **SC-004**: 100% of message content is end-to-end encrypted; the server stores only ciphertext and cannot read any message content
- **SC-005**: Users can scroll through the full message history in a room (all messages retained since room creation, up to the display limit of the session)
- **SC-006**: 95% of users can send their first message in a room without assistance or confusion
- **SC-007**: A room owner can successfully invite a new member in under 30 seconds
- **SC-008**: An invited user sees the new room in their room list within 10 seconds of the invite being sent

## Assumptions

- Users are already authenticated before reaching the room page; the authentication system is handled by a prior feature
- Rooms are invite-only; there is no public discovery or open join flow
- Only the room owner can invite members; regular members cannot extend invites
- "Private messages" refers to messages visible only to all members of a specific room, not one-to-one direct messages between individuals
- A room is always visible to its owner, even before any other members are invited
- Removing members from a room is out of scope for this version
- Messages are text-only for this version; file attachments, images, and reactions are out of scope
- The number of members per room is assumed to be reasonable for a private chat context (e.g., up to ~50 people)
- Notifications for new messages when the user is not on the room page are out of scope for this feature
- Room deletion is out of scope for this version — rooms persist indefinitely; the owner can only delete individual messages (FR-017)
- The Room Gateway (feature 002) must be updated separately to reflect that users see only their invited rooms, not all public rooms
