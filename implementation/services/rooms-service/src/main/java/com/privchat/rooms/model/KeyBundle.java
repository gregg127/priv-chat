package com.privchat.rooms.model;

import java.time.OffsetDateTime;

/**
 * Signal Protocol public key bundle for a user/device.
 * The server stores ONLY public keys — private keys never leave the client.
 */
public record KeyBundle(
        Long id,
        String username,
        int deviceId,
        byte[] identityKey,
        int signedPreKeyId,
        byte[] signedPreKey,
        byte[] signedPreKeySignature,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
