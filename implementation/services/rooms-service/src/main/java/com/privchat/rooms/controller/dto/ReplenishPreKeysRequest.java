package com.privchat.rooms.controller.dto;

import java.util.List;

/**
 * Request body for POST /keys/prekeys/replenish.
 */
public record ReplenishPreKeysRequest(
        int deviceId,
        List<UploadKeyBundleRequest.OneTimePreKeyRequestDto> oneTimePreKeys
) {}
