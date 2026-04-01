package com.privchat.rooms.controller.dto;

import com.privchat.rooms.model.Room;
import com.privchat.rooms.model.RoomMember;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Response DTO for room data. Maps from {@link Room} model.
 */
public record RoomResponse(
        Long id,
        String name,
        String creatorUsername,
        String ownerUsername,
        String createdAt,
        int activeMemberCount,
        List<MemberDto> members
) {
    public record MemberDto(String username, String joinedAt, boolean isOwner) {}

    /** Maps a {@link Room} domain model to a {@link RoomResponse} without members. */
    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.id(),
                room.name(),
                room.creatorUsername(),
                room.ownerUsername(),
                room.createdAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                room.activeMemberCount(),
                null
        );
    }

    /** Maps a {@link Room} domain model plus members list to a {@link RoomResponse}. */
    public static RoomResponse from(Room room, List<RoomMember> members) {
        List<MemberDto> memberDtos = members.stream()
                .map(m -> new MemberDto(
                        m.username(),
                        m.joinedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        m.username().equals(room.ownerUsername())
                ))
                .toList();
        return new RoomResponse(
                room.id(),
                room.name(),
                room.creatorUsername(),
                room.ownerUsername(),
                room.createdAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                room.activeMemberCount(),
                memberDtos
        );
    }
}
