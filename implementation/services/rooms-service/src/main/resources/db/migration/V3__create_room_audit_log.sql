-- V3: Create room_audit_log table
CREATE TABLE room_audit_log (
    id              BIGSERIAL   PRIMARY KEY,
    event_type      VARCHAR(50) NOT NULL CHECK (event_type IN ('CREATE_ROOM', 'UPDATE_ROOM', 'DELETE_ROOM', 'UNAUTHORIZED_ATTEMPT')),
    room_id         BIGINT,
    room_name       VARCHAR(100),
    actor_username  VARCHAR(64) NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_actor_time  ON room_audit_log (actor_username, occurred_at DESC);
CREATE INDEX idx_audit_event_time  ON room_audit_log (event_type, occurred_at DESC);
