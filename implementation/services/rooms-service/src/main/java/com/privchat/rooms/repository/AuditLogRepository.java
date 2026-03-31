package com.privchat.rooms.repository;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.privchat.rooms.jooq.Tables.ROOM_AUDIT_LOG;

/**
 * Append-only audit log repository.
 * Only INSERT operations are permitted — no UPDATE or DELETE.
 */
@Repository
public class AuditLogRepository {

    private final DSLContext dsl;

    public AuditLogRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Inserts an audit log entry. All parameters except {@code roomId} and
     * {@code roomName} may be null (for UNAUTHORIZED_ATTEMPT on non-existent rooms).
     *
     * @param eventType     one of: CREATE_ROOM, UPDATE_ROOM, DELETE_ROOM, UNAUTHORIZED_ATTEMPT
     * @param roomId        nullable — ID of the affected room
     * @param roomName      nullable — name snapshot at event time
     * @param actorUsername username from JWT sub claim (never null)
     */
    public void insert(String eventType, Long roomId, String roomName, String actorUsername) {
        dsl.insertInto(ROOM_AUDIT_LOG)
                .set(ROOM_AUDIT_LOG.EVENT_TYPE, eventType)
                .set(ROOM_AUDIT_LOG.ROOM_ID, roomId)
                .set(ROOM_AUDIT_LOG.ROOM_NAME, roomName)
                .set(ROOM_AUDIT_LOG.ACTOR_USERNAME, actorUsername)
                .execute();
    }
}
