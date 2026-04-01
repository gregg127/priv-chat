package com.privchat.rooms.model;

import java.time.OffsetDateTime;

public record Room(
        Long id,
        String name,
        String creatorUsername,
        String ownerUsername,
        OffsetDateTime createdAt,
        int activeMemberCount,
        long messageSeq
) {}
