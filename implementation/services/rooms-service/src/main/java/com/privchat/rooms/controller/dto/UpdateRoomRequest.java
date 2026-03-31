package com.privchat.rooms.controller.dto;

/**
 * Request DTO for updating a room's name. The {@code name} field is required.
 */
public record UpdateRoomRequest(String name) {}
