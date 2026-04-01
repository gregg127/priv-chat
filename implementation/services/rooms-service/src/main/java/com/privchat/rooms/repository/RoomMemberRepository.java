package com.privchat.rooms.repository;

import com.privchat.rooms.model.RoomMember;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.privchat.rooms.jooq.Tables.ROOM_MEMBERS;

/**
 * jOOQ-based repository for {@link RoomMember} entities.
 */
@Repository
public class RoomMemberRepository {

    private final DSLContext dsl;

    public RoomMemberRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Returns all members of the given room. */
    public List<RoomMember> findByRoomId(Long roomId) {
        return dsl.selectFrom(ROOM_MEMBERS)
                .where(ROOM_MEMBERS.ROOM_ID.eq(roomId))
                .orderBy(ROOM_MEMBERS.JOINED_AT.asc())
                .fetch()
                .map(r -> new RoomMember(
                        r.getRoomId(),
                        r.getUsername(),
                        r.getInvitedBy(),
                        r.getJoinedAt(),
                        r.getJoinSeq()
                ));
    }

    /** Returns IDs of all rooms the given username is a member of. */
    public List<Long> findRoomIdsByUsername(String username) {
        return dsl.select(ROOM_MEMBERS.ROOM_ID)
                .from(ROOM_MEMBERS)
                .where(ROOM_MEMBERS.USERNAME.eq(username))
                .fetch(ROOM_MEMBERS.ROOM_ID);
    }

    /** Checks whether the user is a member of the given room. */
    public boolean isMember(Long roomId, String username) {
        return dsl.fetchExists(dsl.selectFrom(ROOM_MEMBERS)
                .where(ROOM_MEMBERS.ROOM_ID.eq(roomId)
                        .and(ROOM_MEMBERS.USERNAME.eq(username))));
    }

    /** Returns the membership record for a specific user in a specific room. */
    public Optional<RoomMember> findByRoomIdAndUsername(Long roomId, String username) {
        return dsl.selectFrom(ROOM_MEMBERS)
                .where(ROOM_MEMBERS.ROOM_ID.eq(roomId)
                        .and(ROOM_MEMBERS.USERNAME.eq(username)))
                .fetchOptional()
                .map(r -> new RoomMember(
                        r.getRoomId(),
                        r.getUsername(),
                        r.getInvitedBy(),
                        r.getJoinedAt(),
                        r.getJoinSeq()
                ));
    }

    /** Inserts a new room member. Returns the created record. */
    public RoomMember insert(Long roomId, String username, String invitedBy, long joinSeq) {
        var r = dsl.insertInto(ROOM_MEMBERS)
                .set(ROOM_MEMBERS.ROOM_ID, roomId)
                .set(ROOM_MEMBERS.USERNAME, username)
                .set(ROOM_MEMBERS.INVITED_BY, invitedBy)
                .set(ROOM_MEMBERS.JOIN_SEQ, joinSeq)
                .returning()
                .fetchOne();
        return new RoomMember(r.getRoomId(), r.getUsername(), r.getInvitedBy(), r.getJoinedAt(), r.getJoinSeq());
    }

    /** Returns the member with the earliest joined_at (for ownership transfer). */
    public Optional<RoomMember> findOldestMemberExcluding(Long roomId, String excludeUsername) {
        return dsl.selectFrom(ROOM_MEMBERS)
                .where(ROOM_MEMBERS.ROOM_ID.eq(roomId)
                        .and(ROOM_MEMBERS.USERNAME.ne(excludeUsername)))
                .orderBy(ROOM_MEMBERS.JOINED_AT.asc())
                .limit(1)
                .fetchOptional()
                .map(r -> new RoomMember(
                        r.getRoomId(), r.getUsername(), r.getInvitedBy(), r.getJoinedAt(), r.getJoinSeq()
                ));
    }

    /** Counts total members in a room. */
    public int countByRoomId(Long roomId) {
        return dsl.fetchCount(ROOM_MEMBERS, ROOM_MEMBERS.ROOM_ID.eq(roomId));
    }
}
