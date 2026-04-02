/**
 * WebSocketService — manages a single authenticated WebSocket connection.
 *
 * Auth flow:
 *   1. Connect to /ws
 *   2. Immediately send {type:"auth", token}
 *   3. Wait for {type:"auth_ok"} before subscribing to rooms
 *
 * After auth, send {type:"subscribe", roomId} to receive room messages.
 *
 * Usage:
 *   const ws = new WebSocketService(token);
 *   ws.on('message', handler);
 *   ws.subscribe(roomId);
 *   // on cleanup:
 *   ws.close();
 */

export type WsMessageType =
  | 'auth_ok'
  | 'subscribed'
  | 'unsubscribed'
  | 'message'
  | 'message_deleted'
  | 'member_joined'
  | 'member_left'
  | 'ack'
  | 'error';

export interface WsInboundMessage {
  type: WsMessageType | string;
  [key: string]: unknown;
}

export interface WsChatMessage extends WsInboundMessage {
  type: 'message';
  id: number;
  seq: number;
  roomId: number;
  senderUsername: string;
  /** Base64-encoded ciphertext */
  ciphertext: string;
  ciphertextB64?: string; // alias used when building optimistic entries
  clientMessageId: string;
  serverTimestamp: string;
  timestamp?: string;
  /** True for locally-injected optimistic entries that haven't been confirmed by the server yet */
  optimistic?: boolean;
}

export interface WsDeletedMessage extends WsInboundMessage {
  type: 'message_deleted';
  roomId: number;
  messageId: number;
}

export interface WsPresenceMessage extends WsInboundMessage {
  type: 'member_joined' | 'member_left';
  roomId: number;
  username: string;
}

type MessageHandler = (msg: WsInboundMessage) => void;

export class WebSocketService {
  private ws: WebSocket | null = null;
  private token: string;
  private authenticated = false;
  private connectCount = 0;
  private pendingSubscriptions: Set<number> = new Set();
  private handlers: Map<string, Set<MessageHandler>> = new Map();
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private closed = false;
  private reconnectDelay = 1000;

  constructor(token: string) {
    this.token = token;
    this.connect();
  }

  private connect() {
    if (this.closed) return;
    this.connectCount++;

    // NEXT_PUBLIC_* vars are embedded at Docker build time.
    // Use || so an empty-string default also triggers the same-origin fallback.
    const wsUrl = process.env.NEXT_PUBLIC_WS_URL
      || (() => {
        const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
        const host = window.location.host;
        return `${protocol}://${host}/ws`;
      })();

    this.ws = new WebSocket(wsUrl);

    this.ws.onopen = () => {
      this.reconnectDelay = 1000;
      this.ws!.send(JSON.stringify({ type: 'auth', token: this.token }));
    };

    this.ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data) as WsInboundMessage;
        if (msg.type === 'auth_ok') {
          this.authenticated = true;
          const isReconnect = this.connectCount > 1;
          // Re-subscribe to all pending rooms
          this.pendingSubscriptions.forEach(roomId => {
            this.ws!.send(JSON.stringify({ type: 'subscribe', roomId }));
          });
          // Emit a special 'reconnected' event so consumers can trigger catch-up.
          // connectCount > 1 means WS was lost and re-established.
          if (isReconnect) {
            this.emit('reconnected', msg);
          }
        }
        this.emit(msg.type, msg);
        this.emit('*', msg);
      } catch {
        // Malformed server message — ignore
      }
    };

    this.ws.onerror = () => {
      // onclose will handle reconnect
    };

    this.ws.onclose = () => {
      this.authenticated = false;
      if (!this.closed) {
        this.scheduleReconnect();
      }
    };
  }

  private scheduleReconnect() {
    this.reconnectTimer = setTimeout(() => {
      this.reconnectDelay = Math.min(this.reconnectDelay * 2, 30000);
      this.connect();
    }, this.reconnectDelay);
  }

  subscribe(roomId: number) {
    this.pendingSubscriptions.add(roomId);
    if (this.authenticated && this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ type: 'subscribe', roomId }));
    }
  }

  unsubscribe(roomId: number) {
    this.pendingSubscriptions.delete(roomId);
    if (this.authenticated && this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ type: 'unsubscribe', roomId }));
    }
  }

  /**
   * Sends an encrypted message to a room.
   * The ciphertext must already be Signal-encrypted by the caller.
   */
  sendMessage(roomId: number, ciphertextB64: string, clientMessageId: string) {
    if (!this.authenticated || this.ws?.readyState !== WebSocket.OPEN) {
      console.warn('WebSocket not ready — message not sent');
      return;
    }
    this.ws.send(JSON.stringify({
      type: 'message',
      roomId,
      ciphertext: ciphertextB64,
      clientMessageId,
    }));
  }

  on(type: string, handler: MessageHandler) {
    if (!this.handlers.has(type)) this.handlers.set(type, new Set());
    this.handlers.get(type)!.add(handler);
  }

  off(type: string, handler: MessageHandler) {
    this.handlers.get(type)?.delete(handler);
  }

  private emit(type: string, msg: WsInboundMessage) {
    this.handlers.get(type)?.forEach(h => h(msg));
  }

  close() {
    this.closed = true;
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.ws?.close();
  }

  get isOpen(): boolean {
    return this.authenticated && this.ws?.readyState === WebSocket.OPEN;
  }
}
