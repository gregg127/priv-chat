package com.privchat.rooms.model;

import java.time.OffsetDateTime;

public record Room(
        Long id,
        String name,
        String creatorUsername,
        OffsetDateTime createdAt,
        int activeMemberCount
) {}
