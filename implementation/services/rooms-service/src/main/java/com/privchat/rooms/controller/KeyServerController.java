package com.privchat.rooms.controller;

import com.privchat.rooms.controller.dto.ReplenishPreKeysRequest;
import com.privchat.rooms.controller.dto.UploadKeyBundleRequest;
import com.privchat.rooms.model.KeyBundle;
import com.privchat.rooms.repository.AuditLogRepository;
import com.privchat.rooms.service.keyserver.KeyServerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;

/**
 * REST controller for Signal Protocol key material.
 *
 * <p>Server stores ONLY public keys (zero-knowledge design).
 *
 * <ul>
 *   <li>POST /keys/bundles — upload identity + signed prekey bundle</li>
 *   <li>GET  /keys/bundles/{username}/{deviceId} — fetch bundle to start a Signal session</li>
 *   <li>POST /keys/prekeys/replenish — upload additional one-time prekeys</li>
 * </ul>
 */
@RestController
@RequestMapping("/keys")
public class KeyServerController {

    private final KeyServerService keyServerService;
    private final AuditLogRepository auditLogRepository;

    public KeyServerController(KeyServerService keyServerService,
                               AuditLogRepository auditLogRepository) {
        this.keyServerService = keyServerService;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * POST /keys/bundles
     * Uploads (or replaces) the caller's key bundle.
     * <p>Body: {@link UploadKeyBundleRequest} with base64-encoded keys.
     */
    @PostMapping("/bundles")
    public ResponseEntity<?> uploadBundle(@RequestBody UploadKeyBundleRequest request) {
        String username = currentUsername();
        KeyBundle bundle = keyServerService.uploadBundle(username, request);
        // Audit key bundle registration — required by constitution §II security requirements.
        // Logs the event without key material (zero-knowledge logging).
        auditLogRepository.insert("KEY_BUNDLE_REGISTER", null, null, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "username", bundle.username(),
                "deviceId", bundle.deviceId(),
                "createdAt", bundle.createdAt().toString()
        ));
    }

    /**
     * GET /keys/bundles/{username}/{deviceId}
     * Fetches the public key bundle for a user to establish a Signal session.
     * Consumes one one-time prekey atomically.
     */
    @GetMapping("/bundles/{username}/{deviceId}")
    public ResponseEntity<?> fetchBundle(@PathVariable String username,
                                         @PathVariable int deviceId) {
        return keyServerService.fetchBundle(username, deviceId)
                .map(r -> {
                    var kb = r.bundle();
                    var otp = r.oneTimePreKey();
                    var body = new java.util.LinkedHashMap<String, Object>();
                    body.put("identityKey", Base64.getEncoder().encodeToString(kb.identityKey()));
                    body.put("signedPrekeyId", kb.signedPreKeyId());
                    body.put("signedPrekey", Base64.getEncoder().encodeToString(kb.signedPreKey()));
                    body.put("signedPrekeySignature", Base64.getEncoder().encodeToString(kb.signedPreKeySignature()));
                    if (otp != null) {
                        body.put("oneTimePrekeyId", otp.keyId());
                        body.put("oneTimePrekey", Base64.getEncoder().encodeToString(otp.publicKey()));
                    }
                    return ResponseEntity.ok(body);
                })
                .<ResponseEntity<?>>map(r -> r)
                .orElse(ResponseEntity.status(404).body(Map.of("error", "Key bundle not found")));
    }

    /**
     * POST /keys/prekeys/replenish
     * Adds more one-time prekeys to the caller's pool.
     */
    @PostMapping("/prekeys/replenish")
    public ResponseEntity<?> replenishPrekeys(@RequestBody ReplenishPreKeysRequest request) {
        String username = currentUsername();
        int stored = keyServerService.replenishPrekeys(username, request.deviceId(), request.oneTimePreKeys());
        return ResponseEntity.ok(Map.of("stored", stored));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
