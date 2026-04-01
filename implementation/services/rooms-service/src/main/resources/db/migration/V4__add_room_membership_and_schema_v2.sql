-- V4: Invite-only membership model, owner tracking, and message sequencing
--
-- Changes:
--   1. Add owner_username to rooms (tracks current owner; can transfer)
--   2. Add message_seq to rooms (atomic per-room sequence counter)
--   3. Create room_members table (invite-based membership)
--   4. Backfill: existing room creators become owner + first member
--   5. Update audit event_type constraint to include new events

-- 1. Add owner and message sequencer to rooms
ALTER TABLE rooms ADD COLUMN owner_username VARCHAR(64);
UPDATE rooms SET owner_username = creator_username;
ALTER TABLE rooms ALTER COLUMN owner_username SET NOT NULL;

ALTER TABLE rooms ADD COLUMN message_seq BIGINT NOT NULL DEFAULT 0;

-- 2. room_members: one row per (room, user) — the invite record
CREATE TABLE room_members (
    room_id     BIGINT       NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    username    VARCHAR(64)  NOT NULL,
    invited_by  VARCHAR(64)  NOT NULL,
    joined_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    join_seq    BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (room_id, username)
);

-- 3. Backfill: make existing room creators members of their own rooms
--    (uses plain INSERT — safe because no room_members rows existed before this migration)
INSERT INTO room_members (room_id, username, invited_by, joined_at, join_seq)
SELECT id, creator_username, creator_username, created_at, 0
FROM rooms;

CREATE INDEX idx_room_members_username    ON room_members (username);
CREATE INDEX idx_room_members_room_joined ON room_members (room_id, joined_at);

-- 4. Widen audit log constraint to accept new event types
ALTER TABLE room_audit_log DROP CONSTRAINT IF EXISTS room_audit_log_event_type_check;
ALTER TABLE room_audit_log ADD CONSTRAINT room_audit_log_event_type_check
    CHECK (event_type IN (
        'CREATE_ROOM', 'UPDATE_ROOM', 'DELETE_ROOM', 'UNAUTHORIZED_ATTEMPT',
        'INVITE_MEMBER', 'DELETE_MESSAGE', 'TRANSFER_OWNERSHIP'
    ));
