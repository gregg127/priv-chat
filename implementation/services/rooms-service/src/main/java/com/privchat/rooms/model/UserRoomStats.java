package com.privchat.rooms.model;

public record UserRoomStats(
        String username,
        int roomsCreatedCount,
        int activeRoomsCount
) {}
