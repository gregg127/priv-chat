package com.privchat.rooms.controller.dto;

/**
 * Request DTO for creating a room. The {@code name} field is optional —
 * if absent, the server generates {@code {username}-room-{n}}.
 */
public record CreateRoomRequest(String name) {}
