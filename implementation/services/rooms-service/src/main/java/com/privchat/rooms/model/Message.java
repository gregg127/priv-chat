package com.privchat.rooms.model;

import java.time.OffsetDateTime;

/**
 * A stored E2E-encrypted message. The server holds ciphertext only — plaintext is
 * never present on the server side (zero-knowledge design).
 */
public record Message(
        Long id,
        Long roomId,
        long seq,
        String senderUsername,
        byte[] ciphertext,
        java.util.UUID clientMessageId,
        OffsetDateTime serverTimestamp,
        OffsetDateTime deletedAt
) {}
