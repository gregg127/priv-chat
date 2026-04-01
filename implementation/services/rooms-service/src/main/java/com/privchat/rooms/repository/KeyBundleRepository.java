package com.privchat.rooms.repository;

import com.privchat.rooms.model.KeyBundle;
import com.privchat.rooms.model.OneTimePreKey;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.privchat.rooms.jooq.Tables.KEY_BUNDLES;
import static com.privchat.rooms.jooq.Tables.ONE_TIME_PREKEYS;

/**
 * Repository for Signal Protocol public key material.
 * Server stores ONLY public keys — private keys never leave the client.
 */
@Repository
public class KeyBundleRepository {

    private final DSLContext dsl;

    public KeyBundleRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ─── KeyBundle ────────────────────────────────────────────────────────────

    /** Upserts a key bundle for the given user/device. */
    public KeyBundle upsertBundle(String username, int deviceId,
                                  byte[] identityKey, int signedPreKeyId,
                                  byte[] signedPreKey, byte[] signedPreKeySig) {
        var r = dsl.insertInto(KEY_BUNDLES)
                .set(KEY_BUNDLES.USERNAME, username)
                .set(KEY_BUNDLES.DEVICE_ID, deviceId)
                .set(KEY_BUNDLES.IDENTITY_KEY, identityKey)
                .set(KEY_BUNDLES.SIGNED_PREKEY_ID, signedPreKeyId)
                .set(KEY_BUNDLES.SIGNED_PREKEY, signedPreKey)
                .set(KEY_BUNDLES.SIGNED_PREKEY_SIGNATURE, signedPreKeySig)
                .onConflict(KEY_BUNDLES.USERNAME, KEY_BUNDLES.DEVICE_ID)
                .doUpdate()
                .set(KEY_BUNDLES.SIGNED_PREKEY_ID, signedPreKeyId)
                .set(KEY_BUNDLES.SIGNED_PREKEY, signedPreKey)
                .set(KEY_BUNDLES.SIGNED_PREKEY_SIGNATURE, signedPreKeySig)
                .set(KEY_BUNDLES.UPDATED_AT, org.jooq.impl.DSL.currentOffsetDateTime())
                .returning()
                .fetchOne();
        return toBundleModel(r);
    }

    /** Returns the key bundle for a user's device, if registered. */
    public Optional<KeyBundle> findBundle(String username, int deviceId) {
        return dsl.selectFrom(KEY_BUNDLES)
                .where(KEY_BUNDLES.USERNAME.eq(username).and(KEY_BUNDLES.DEVICE_ID.eq(deviceId)))
                .fetchOptional()
                .map(this::toBundleModel);
    }

    private KeyBundle toBundleModel(org.jooq.Record r) {
        var kb = (com.privchat.rooms.jooq.tables.records.KeyBundlesRecord) r;
        return new KeyBundle(kb.getId(), kb.getUsername(), kb.getDeviceId(),
                kb.getIdentityKey(), kb.getSignedPrekeyId(), kb.getSignedPrekey(),
                kb.getSignedPrekeySignature(), kb.getCreatedAt(), kb.getUpdatedAt());
    }

    // ─── OneTimePreKey ────────────────────────────────────────────────────────

    /** Inserts a batch of one-time prekeys. Ignores duplicates (key_id already exists). */
    public int insertOneTimePreKeys(String username, int deviceId, List<int[]> keyPairs) {
        // keyPairs: each int[] is { keyId, publicKeyBytes... } — use dedicated method below
        throw new UnsupportedOperationException("Use insertOneTimePreKeyBatch");
    }

    /** Inserts a batch of one-time prekeys. Returns count inserted. */
    public int insertOneTimePreKeyBatch(String username, int deviceId,
                                        List<com.privchat.rooms.controller.dto.OneTimePreKeyDto> keys) {
        int inserted = 0;
        for (var k : keys) {
            int rows = dsl.insertInto(ONE_TIME_PREKEYS)
                    .set(ONE_TIME_PREKEYS.USERNAME, username)
                    .set(ONE_TIME_PREKEYS.DEVICE_ID, deviceId)
                    .set(ONE_TIME_PREKEYS.KEY_ID, k.keyId())
                    .set(ONE_TIME_PREKEYS.PUBLIC_KEY, k.publicKey())
                    .onConflict(ONE_TIME_PREKEYS.USERNAME, ONE_TIME_PREKEYS.DEVICE_ID, ONE_TIME_PREKEYS.KEY_ID)
                    .doNothing()
                    .execute();
            inserted += rows;
        }
        return inserted;
    }

    /**
     * Atomically fetches and deletes one OTP for the given user/device.
     * Returns empty if the pool is exhausted (X3DH proceeds without OTP).
     */
    public Optional<OneTimePreKey> fetchAndDeleteOneTimePreKey(String username, int deviceId) {
        return dsl.deleteFrom(ONE_TIME_PREKEYS)
                .where(ONE_TIME_PREKEYS.ID.eq(
                        dsl.select(ONE_TIME_PREKEYS.ID).from(ONE_TIME_PREKEYS)
                                .where(ONE_TIME_PREKEYS.USERNAME.eq(username)
                                        .and(ONE_TIME_PREKEYS.DEVICE_ID.eq(deviceId)))
                                .orderBy(ONE_TIME_PREKEYS.CREATED_AT.asc())
                                .limit(1)
                ))
                .returning()
                .fetchOptional()
                .map(r -> new OneTimePreKey(r.getId(), r.getUsername(), r.getDeviceId(),
                        r.getKeyId(), r.getPublicKey(), r.getCreatedAt()));
    }

    /** Returns the current pool size for a user/device. */
    public int poolSize(String username, int deviceId) {
        return dsl.fetchCount(ONE_TIME_PREKEYS,
                ONE_TIME_PREKEYS.USERNAME.eq(username).and(ONE_TIME_PREKEYS.DEVICE_ID.eq(deviceId)));
    }
}
