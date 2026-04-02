/**
 * Signal Protocol client stub — ⚠️ NOT FOR PRODUCTION USE ⚠️
 *
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  TECH DEBT — TRACKED: 004-e2e-encryption                        ║
 * ║  Constitution violations: C1 (no real encryption), C2 (WebCrypto║
 * ║  API not used). Messages sent with this stub are stored as       ║
 * ║  readable plaintext on the server. DO NOT use in production or   ║
 * ║  with real private data.                                         ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * This stub provides the correct call signature so all other modules compile
 * and the room/chat UX can be developed and tested independently of the
 * encryption layer.
 *
 * Full replacement requires:
 *   1. `npm install @signalapp/libsignal-client` (WASM, needs Next.js webpack config)
 *   2. Identity keypair generation + IndexedDB persistence on first login
 *   3. X3DH session establishment with each peer (using /keys/bundles endpoint)
 *   4. SenderKey group session per room — distribute via X3DH-encrypted messages
 *   5. Encrypt messages with SenderKeyMessage; decrypt with stored SenderKey chains
 *   6. Handle SenderKeyDistributionMessage on new member join
 *
 * See:
 *   - specs/004-e2e-encryption/ (feature branch — not yet created)
 *   - Signal Protocol spec: https://signal.org/docs/specifications/doubleratchet/
 *   - libsignal-client: https://github.com/signalapp/libsignal
 */

/** ⚠️ STUB — NOT ENCRYPTED. Returns base64-encoded UTF-8 plaintext. */
export function encryptMessage(plaintext: string, _roomId: number): string {
  if (process.env.NODE_ENV !== 'test') {
    console.warn(
      '[signal] encryptMessage() is a stub — message is NOT encrypted. ' +
      'See signalClient.ts for the implementation roadmap (feature 004-e2e-encryption).'
    );
  }
  return btoa(unescape(encodeURIComponent(plaintext)));
}

/** ⚠️ STUB — NOT DECRYPTED. Reverses the base64-encoded UTF-8 stub encoding. */
export function decryptMessage(ciphertextB64: string, _roomId: number): string | null {
  try {
    return decodeURIComponent(escape(atob(ciphertextB64)));
  } catch {
    return null;
  }
}

/** Generates a random UUID for use as clientMessageId. */
export function generateClientMessageId(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

/**
 * Returns true when the real Signal Protocol implementation is in place.
 * Used by the UI to show an "unencrypted" warning banner (see FR-006 compliance).
 */
export const IS_ENCRYPTION_STUB = true;
