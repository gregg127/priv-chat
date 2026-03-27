package com.privchat.auth.controller.dto;

public record SessionResponse(boolean authenticated, String username) {}
