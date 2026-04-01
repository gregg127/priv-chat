-- V1: Create rooms table
CREATE TABLE rooms (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100) UNIQUE NOT NULL,
    creator_username VARCHAR(64)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    active_member_count INT        NOT NULL DEFAULT 0 CHECK (active_member_count >= 0)
);

CREATE INDEX idx_rooms_created_at    ON rooms (created_at DESC);
CREATE INDEX idx_rooms_creator       ON rooms (creator_username);
