-- V8: Widen audit log event_type constraint to include KEY_BUNDLE_REGISTER.
--
-- Key bundle registration events are required by the project constitution §II
-- ("key registration events MUST be logged server-side in a tamper-evident manner").
-- No key material is stored — only the actor username and timestamp.

ALTER TABLE room_audit_log DROP CONSTRAINT IF EXISTS room_audit_log_event_type_check;
ALTER TABLE room_audit_log ADD CONSTRAINT room_audit_log_event_type_check
    CHECK (event_type IN (
        'CREATE_ROOM', 'UPDATE_ROOM', 'DELETE_ROOM', 'UNAUTHORIZED_ATTEMPT',
        'INVITE_MEMBER', 'DELETE_MESSAGE', 'TRANSFER_OWNERSHIP',
        'KEY_BUNDLE_REGISTER'
    ));
