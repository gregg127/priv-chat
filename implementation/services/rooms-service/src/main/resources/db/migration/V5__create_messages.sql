-- V5: Message storage (E2E ciphertext only — server never stores plaintext)
--
-- Design:
--   - seq is assigned server-side via atomic UPDATE on rooms.message_seq
--   - ciphertext is Signal Protocol SenderKeyMessage bytes (opaque to server)
--   - client_message_id prevents duplicate delivery on client retry
--   - deleted_at is soft-delete (owner action); hard-deleted rows not retrievable

CREATE TABLE messages (
    id                BIGSERIAL    PRIMARY KEY,
    room_id           BIGINT       NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    seq               BIGINT       NOT NULL,
    sender_username   VARCHAR(64)  NOT NULL,
    ciphertext        BYTEA        NOT NULL,
    client_message_id UUID         NOT NULL,
    server_timestamp  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ,
    UNIQUE (room_id, seq),
    UNIQUE (room_id, client_message_id)
);

-- Primary access pattern: fetch room messages ordered by seq, respecting join_seq boundary
CREATE INDEX idx_messages_room_seq ON messages (room_id, seq);
-- Attribution lookup (sender name display)
CREATE INDEX idx_messages_sender   ON messages (sender_username);
-- Fast count / existence check for active (non-deleted) messages
CREATE INDEX idx_messages_active   ON messages (room_id, seq) WHERE deleted_at IS NULL;
