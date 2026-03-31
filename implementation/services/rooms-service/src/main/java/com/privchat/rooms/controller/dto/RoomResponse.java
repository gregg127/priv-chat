package com.privchat.rooms.controller.dto;

import com.privchat.rooms.model.Room;

import java.time.format.DateTimeFormatter;

/**
 * Response DTO for room data. Maps from {@link Room} model.
 */
public record RoomResponse(
        Long id,
        String name,
        String creatorUsername,
        String createdAt,
        int activeMemberCount
) {
    /** Maps a {@link Room} domain model to a {@link RoomResponse}. */
    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.id(),
                room.name(),
                room.creatorUsername(),
                room.createdAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                room.activeMemberCount()
        );
    }
}
