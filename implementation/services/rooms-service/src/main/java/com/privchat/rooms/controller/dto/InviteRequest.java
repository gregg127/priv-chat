package com.privchat.rooms.controller.dto;

/**
 * Request body for inviting a user to a room.
 * Field: {@code username} — the target user's login name.
 */
public record InviteRequest(String username) {}
