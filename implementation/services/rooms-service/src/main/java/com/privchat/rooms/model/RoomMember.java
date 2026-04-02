package com.privchat.rooms.model;

import java.time.OffsetDateTime;

/**
 * Represents a user's invite-based membership in a room.
 * {@code joinSeq} is the per-room sequence number at invite time —
 * messages with seq < joinSeq are not accessible to this member.
 */
public record RoomMember(
        Long roomId,
        String username,
        String invitedBy,
        OffsetDateTime joinedAt,
        long joinSeq
) {}
