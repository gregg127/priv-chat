package com.privchat.rooms.model;

import java.time.OffsetDateTime;

public record RoomAuditLog(
        Long id,
        String eventType,
        Long roomId,
        String roomName,
        String actorUsername,
        OffsetDateTime occurredAt
) {}
