-- V6: Signal Protocol public key storage
--
-- Server stores ONLY public keys — private keys never leave the client device.
-- key_bundles: identity key + signed prekey per user/device
-- one_time_prekeys: ephemeral X3DH one-time prekey pool (consumed on fetch)

CREATE TABLE key_bundles (
    id                      BIGSERIAL    PRIMARY KEY,
    username                VARCHAR(64)  NOT NULL,
    device_id               INT          NOT NULL DEFAULT 1,
    identity_key            BYTEA        NOT NULL,
    signed_prekey_id        INT          NOT NULL,
    signed_prekey           BYTEA        NOT NULL,
    signed_prekey_signature BYTEA        NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (username, device_id)
);

CREATE TABLE one_time_prekeys (
    id         BIGSERIAL    PRIMARY KEY,
    username   VARCHAR(64)  NOT NULL,
    device_id  INT          NOT NULL DEFAULT 1,
    key_id     INT          NOT NULL,
    public_key BYTEA        NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (username, device_id, key_id)
);

-- Pool size and fetch queries
CREATE INDEX idx_otp_user_device ON one_time_prekeys (username, device_id);
