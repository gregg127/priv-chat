/**
 * Signal Protocol stub for the browser.
 *
 * In a full implementation this would use @signalapp/libsignal-client (WASM).
 * This stub provides the correct interface so all other code compiles and runs;
 * it passes ciphertext through as base64-encoded UTF-8.
 *
 * TODO (Phase 6 completion): Replace stub with real libsignal-client calls:
 *   1. npm install @signalapp/libsignal-client
 *   2. Call SignalClient.initializeLibsignal() on app startup
 *   3. Implement SenderKeyDistribution for group messaging
 *   4. Encrypt/decrypt with SenderKeyMessage
 */

/** Encodes a plaintext string to a base64 "ciphertext" (STUB — no encryption). */
export function encryptMessage(plaintext: string, _roomId: number): string {
  // STUB: real impl would use Signal SenderKey group encryption
  return btoa(unescape(encodeURIComponent(plaintext)));
}

/** Decodes a base64 "ciphertext" back to plaintext (STUB — no decryption). */
export function decryptMessage(ciphertextB64: string, _roomId: number): string | null {
  // STUB: real impl would use Signal SenderKey group decryption
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
  // Fallback for environments without crypto.randomUUID
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}
