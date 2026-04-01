package com.privchat.rooms.service.keyserver;

import com.privchat.rooms.controller.dto.OneTimePreKeyDto;
import com.privchat.rooms.controller.dto.UploadKeyBundleRequest;
import com.privchat.rooms.model.KeyBundle;
import com.privchat.rooms.model.OneTimePreKey;
import com.privchat.rooms.repository.KeyBundleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Manages Signal Protocol public key material (zero-knowledge server design).
 *
 * <p>The server stores only public keys. Private keys never leave the client.
 * Key material is uploaded once per device; one-time prekeys are replenished
 * in batches when the supply runs low.
 */
@Service
public class KeyServerService {

    /** Minimum OTP count before the client should replenish. */
    static final int LOW_WATER_MARK = 5;

    private final KeyBundleRepository keyBundleRepository;

    public KeyServerService(KeyBundleRepository keyBundleRepository) {
        this.keyBundleRepository = keyBundleRepository;
    }

    /**
     * Uploads or replaces a key bundle for the given user/device.
     * Replaces the bundle atomically (upsert).
     */
    @Transactional
    public KeyBundle uploadBundle(String username, UploadKeyBundleRequest req) {
        byte[] identityKey = Base64.getDecoder().decode(req.identityKey());
        byte[] signedPrekey = Base64.getDecoder().decode(req.signedPreKey().publicKey());
        byte[] signedPrekeySignature = Base64.getDecoder().decode(req.signedPreKey().signature());
        return keyBundleRepository.upsertBundle(username, req.deviceId(), identityKey,
                req.signedPreKey().keyId(), signedPrekey, signedPrekeySignature);
    }

    /**
     * Replenishes one-time prekeys for the given user/device.
     *
     * @return number of keys actually stored
     */
    @Transactional
    public int replenishPrekeys(String username, int deviceId,
                                List<UploadKeyBundleRequest.OneTimePreKeyRequestDto> keys) {
        var decoded = keys.stream()
                .map(k -> OneTimePreKeyDto.fromBase64(k.keyId(), k.publicKey()))
                .toList();
        return keyBundleRepository.insertOneTimePreKeyBatch(username, deviceId, decoded);
    }

    /**
     * Fetches a complete key bundle for establishing a Signal session with a user.
     * Consumes one one-time prekey atomically (returns the bundle + consumed OTP).
     *
     * @return bundle if found, empty otherwise
     */
    @Transactional
    public Optional<KeyBundleResponse> fetchBundle(String targetUsername, int deviceId) {
        Optional<KeyBundle> bundleOpt = keyBundleRepository.findBundle(targetUsername, deviceId);
        if (bundleOpt.isEmpty()) return Optional.empty();

        KeyBundle bundle = bundleOpt.get();
        Optional<OneTimePreKey> otp = keyBundleRepository.fetchAndDeleteOneTimePreKey(targetUsername, deviceId);
        return Optional.of(new KeyBundleResponse(bundle, otp.orElse(null)));
    }

    /**
     * Returns true if the user's OTP count is below the low-water mark.
     * Used by the client to decide whether to replenish.
     */
    public boolean needsReplenishment(String username, int deviceId) {
        return keyBundleRepository.poolSize(username, deviceId) < LOW_WATER_MARK;
    }

    /** Projection returned to callers (does not expose private keys — there are none server-side). */
    public record KeyBundleResponse(KeyBundle bundle, OneTimePreKey oneTimePreKey) {}
}
