package com.privchat.rooms.controller.dto;

import java.util.List;

/**
 * Request body for POST /keys/bundles — upload or refresh a user's key bundle.
 */
public record UploadKeyBundleRequest(
        int deviceId,
        String identityKey,
        SignedPreKeyDto signedPreKey,
        List<OneTimePreKeyRequestDto> oneTimePreKeys
) {
    public record SignedPreKeyDto(int keyId, String publicKey, String signature) {}
    public record OneTimePreKeyRequestDto(int keyId, String publicKey) {}
}
