# Feature Specification: Network Entry Gate

**Feature Branch**: `001-network-entry-gate`
**Created**: 2026-03-26
**Status**: Draft
**Input**: User description: "main page that asks for username and password to enter the portal. The page has a button 'Join network'. Username is a text that user types in and will be his identification, password is a one, global shared password. I mean that if you want to enter the portal you have to know the hardcoded password, it is first, simple entry gate for anyone to join the portal"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Joining the Network (Priority: P1)

A new visitor arrives at the portal's landing page. They choose a display name
(username) that will identify them inside the chat, and they enter the shared
network password that was given to them out-of-band (e.g., via invitation). On
success they are admitted into the portal.

**Why this priority**: This is the only entry point to the entire application.
Nothing else is accessible until this gate is passed. Without it there is no
usable product.

**Independent Test**: Open the landing page in a browser, enter a valid
username and the correct network password, click "Join network", and verify the
user lands inside the portal. Deliverable value: a user can enter the portal.

**Acceptance Scenarios**:

1. **Given** the landing page is open and no session exists, **When** the user
   enters a non-empty username and the correct network password and clicks
   "Join network", **Then** they are admitted to the portal and their chosen
   username is associated with their session.

2. **Given** the landing page is open, **When** the user enters an incorrect
   network password and clicks "Join network", **Then** the entry is rejected,
   an error message is shown ("Incorrect network password"), and the user
   remains on the landing page.

3. **Given** the landing page is open, **When** the user leaves the username
   field empty and clicks "Join network", **Then** the form is not submitted,
   and the user is prompted to enter a username.

4. **Given** the landing page is open, **When** the user leaves the password
   field empty and clicks "Join network", **Then** the form is not submitted,
   and the user is prompted to enter the network password.

---

### User Story 2 - Returning to the Portal (Priority: P2)

A user who has previously joined the network returns to the portal. They should
not need to re-enter credentials if their session is still valid.

**Why this priority**: Repeated re-authentication on every visit degrades
usability for legitimate members. Once admitted, continuity of session is
expected.

**Independent Test**: Join the network in Story 1, close and reopen the
browser tab (without clearing session), and verify the user lands directly
inside the portal without seeing the entry gate again.

**Acceptance Scenarios**:

1. **Given** a user has a valid active session, **When** they navigate to the
   portal URL, **Then** they bypass the entry gate and go directly to the
   portal interior.

2. **Given** a user's session has expired or been cleared, **When** they
   navigate to the portal URL, **Then** they are redirected to the entry gate
   landing page with their last-used username pre-filled and the password field
   empty.

---

### Edge Cases

- What happens when a username that is already in use (by an active session) is
  entered? → The system allows it for now (usernames are display names, not
  unique accounts). Uniqueness enforcement is out of scope for this feature.
- What happens when the user submits the form while the network is unreachable?
  → A user-facing error ("Unable to connect — please try again") is shown.
- What happens when the username contains special characters or is excessively
  long? → The system trims whitespace and enforces a maximum display-name
  length of 64 characters; submission with only whitespace is treated as empty.
- What happens when an IP is rate-limited? → Further join attempts from that IP
  within the lockout window are rejected immediately with a "Too many attempts —
  please wait" message. The remaining lockout duration MUST be visible to the
  user (e.g., "Try again in 8 minutes").

## Clarifications

### Session 2026-03-26

- Q: Should failed password attempts trigger a rate limit or lockout to prevent brute-forcing the shared password? → A: Rate-limit by IP: temporary lockout after 5 failed attempts in 10 minutes
- Q: How should user sessions be stored and managed? → A: Server-side sessions: session ID stored in a secure cookie; session data lives on the server
- Q: What feedback should the user see while the join request is being processed? → A: Disable the "Join network" button and show a loading spinner until the response arrives
- Q: Should the username field be pre-filled with the user's last-used display name when they return after session expiry? → A: Pre-fill username from the expired session (display name remembered in browser storage)
- Q: Which security events on the entry gate should be logged server-side? → A: Log failed attempts (IP, timestamp), successful joins (username, IP, timestamp), and rate-limit triggers (IP, timestamp)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST display a landing page with a username text
  field, a password field, and a "Join network" button as the sole entry point
  to the portal.
- **FR-002**: The system MUST validate that the entered network password matches
  the configured shared network password before admitting a user.
- **FR-003**: The system MUST reject entry and display the message "Incorrect network
  password" when the wrong password is submitted. The error message MUST NOT provide
  any hints about the correct password value (e.g., no "close", "almost", or partial
  match feedback). Username values are not validated against any account store and
  MUST NOT be referenced in error messages.
- **FR-004**: The system MUST require a non-empty, non-whitespace-only username
  before allowing form submission.
- **FR-005**: The system MUST associate the chosen username with the user's
  session upon successful entry, making it available as their display identity
  inside the portal.
- **FR-006**: The system MUST maintain the session server-side so that a user
  with a valid active session is not required to re-enter credentials on
  subsequent visits. The session ID MUST be stored in an HttpOnly, Secure,
  SameSite=Strict cookie. Session data MUST reside on the server and MUST be
  invalidated server-side on expiry or explicit sign-out.
- **FR-007**: The system MUST redirect unauthenticated requests for portal
  interior pages back to the landing page.
- **FR-008**: The system MUST enforce a maximum username length of 64
  characters and trim leading/trailing whitespace.
- **FR-009**: The system MUST rate-limit join attempts by IP address: after 5
  failed password attempts within a 10-minute window, further attempts from
  that IP MUST be rejected with a "Too many attempts — please wait" message
  until the lockout period expires.
- **FR-010**: While a join request is in flight, the system MUST disable the
  "Join network" button and display a loading spinner. The button MUST be
  re-enabled (and the spinner removed) once the response is received.
- **FR-011**: When a returning user's session has expired and they are
  redirected to the entry gate, the system MUST pre-fill the username field
  with their last-used display name, stored in browser-local storage. The
  password field MUST remain empty.
- **FR-012**: The system MUST log the following security events server-side
  with IP address and timestamp (no message content or passwords logged):
  - Failed join attempt (wrong password) — `username` field MUST be null (do not log
    the attempted display name)
  - Successful join — includes chosen username, IP, and timestamp
  - Rate-limit trigger (IP threshold exceeded) — `username` field MUST be null

### Key Entities

- **Session**: Represents an authenticated presence in the portal. Stored
  server-side; identified by an opaque session ID delivered to the client via
  an HttpOnly, Secure, SameSite=Strict cookie. Holds the user's chosen display
  name and a validity period. Created on successful entry; destroyed on expiry
  or explicit sign-out. Server-side storage enables immediate revocation if the
  network password changes.
- **Network Password**: A single shared secret that gates access to the portal.
  Configured at deployment time; not user-specific.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new user can complete the join flow (open page → fill in
  credentials → enter portal) in under 30 seconds on a standard connection.
- **SC-002**: 100% of attempts with an incorrect network password are rejected;
  no false admissions occur.
- **SC-003**: 100% of unauthenticated requests to portal interior pages are
  redirected to the landing page.
- **SC-004**: A returning user with a valid session reaches the portal interior
  without any re-authentication step.
- **SC-005**: The entry gate page is functional on all major evergreen desktop
  and mobile browsers (Chrome, Firefox, Safari, Edge — current and one prior
  major version).

## Assumptions

- The network password is a single value set at deployment time by the operator;
  changing it requires a deployment update (no admin UI for this feature).
- Username uniqueness across active sessions is not enforced in this feature;
  display-name collisions are a known limitation accepted for v1.
- No account registration or password-recovery flow exists; users learn the
  network password out-of-band.
- Session validity period follows a reasonable default (e.g., 24 hours of
  inactivity); exact duration is configurable at deployment.
- This entry gate is a convenience/obscurity layer only, consistent with the
  project constitution's Privacy by Design principle — it is NOT a substitute
  for the end-to-end encryption that protects message content.
