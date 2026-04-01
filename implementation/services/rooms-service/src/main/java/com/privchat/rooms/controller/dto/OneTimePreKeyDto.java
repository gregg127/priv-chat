package com.privchat.rooms.controller.dto;

import java.util.Base64;

/** DTO for a single one-time prekey in upload requests. */
public record OneTimePreKeyDto(int keyId, byte[] publicKey) {

    /** Factory: decodes base64-encoded public key from JSON. */
    public static OneTimePreKeyDto fromBase64(int keyId, String base64PublicKey) {
        return new OneTimePreKeyDto(keyId, Base64.getDecoder().decode(base64PublicKey));
    }

    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey);
    }
}
