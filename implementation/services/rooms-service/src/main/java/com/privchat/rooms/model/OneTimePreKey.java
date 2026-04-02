package com.privchat.rooms.model;

import java.time.OffsetDateTime;

/**
 * A single-use ephemeral X3DH one-time prekey. Deleted from the server
 * immediately after being fetched for key exchange.
 */
public record OneTimePreKey(
        Long id,
        String username,
        int deviceId,
        int keyId,
        byte[] publicKey,
        OffsetDateTime createdAt
) {}
