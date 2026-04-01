'use client';

import { useEffect, useRef, useState } from 'react';
import { decryptMessage } from '@/lib/signal/signalClient';
import type { WsChatMessage } from '@/lib/websocket/WebSocketService';
import type { MessageResponse } from '@/lib/roomsApi';

export interface DisplayMessage {
  id: number;
  seq: number;
  senderUsername: string;
  text: string | null;
  clientMessageId: string;
  serverTimestamp: string;
  isOwn: boolean;
  optimistic?: boolean;
}

interface ChatAreaProps {
  roomId: number;
  currentUser: string;
  /** Initial messages loaded from REST history endpoint */
  initialMessages: MessageResponse[];
  /** Live messages pushed over WebSocket */
  liveMessages: WsChatMessage[];
  onSend: (text: string) => void;
  onDeleteMessage?: (messageId: number) => void;
}

/** Stable per-user avatar color derived from the username. */
const USER_COLORS = [
  '#e74c3c', '#e67e22', '#f1c40f', '#2ecc71',
  '#1abc9c', '#3498db', '#9b59b6', '#e91e63',
  '#00bcd4', '#ff5722',
];
function userColor(username: string): string {
  let h = 0;
  for (let i = 0; i < username.length; i++) h = (h * 31 + username.charCodeAt(i)) >>> 0;
  return USER_COLORS[h % USER_COLORS.length];
}

/**
 * Chat area: renders message history + live messages, input box.
 * - All messages show the sender's name so conversations are attributable.
 * - Own messages are right-aligned and tinted; others are left-aligned.
 * - Delete (✕) appears only on the current user's own messages.
 * - Empty state shown when there are no messages (FR-011).
 */
export default function ChatArea({
  roomId,
  currentUser,
  initialMessages,
  liveMessages,
  onSend,
  onDeleteMessage,
}: ChatAreaProps) {
  const [inputText, setInputText] = useState('');
  const [inputError, setInputError] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [initialMessages.length, liveMessages.length]);

  const allMessages: DisplayMessage[] = [
    ...initialMessages.map(m => ({
      id: m.id,
      seq: m.seq,
      senderUsername: m.senderUsername,
      text: decryptMessage(m.ciphertext, roomId),
      clientMessageId: m.clientMessageId,
      serverTimestamp: m.serverTimestamp,
      isOwn: m.senderUsername === currentUser,
    })),
    ...liveMessages
      .filter(m => !initialMessages.some(im => im.clientMessageId === m.clientMessageId))
      .map(m => ({
        id: m.id,
        seq: m.seq,
        senderUsername: m.senderUsername,
        text: decryptMessage(m.ciphertext, roomId),
        clientMessageId: m.clientMessageId,
        serverTimestamp: m.serverTimestamp,
        isOwn: m.senderUsername === currentUser,
        optimistic: m.optimistic,
      })),
  ].sort((a, b) => {
    // Optimistic messages (seq undefined) always render at the bottom.
    if (a.seq == null) return 1;
    if (b.seq == null) return -1;
    return a.seq - b.seq;
  });

  function handleSend() {
    const trimmed = inputText.trim();
    if (!trimmed) {
      setInputError('Message cannot be empty');
      return;
    }
    setInputError(null);
    onSend(trimmed);
    setInputText('');
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Message list */}
      <div
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: '16px',
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
        }}
        aria-label="Messages"
        aria-live="polite"
      >
        {allMessages.length === 0 ? (
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              height: '100%',
              color: '#888',
              gap: 8,
            }}
            aria-label="No messages yet"
          >
            <span style={{ fontSize: 32 }}>💬</span>
            <p style={{ margin: 0 }}>No messages yet. Say hello!</p>
          </div>
        ) : (
          allMessages.map(msg => {
            const color = userColor(msg.senderUsername);
            return (
              <div
                key={msg.clientMessageId}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: msg.isOwn ? 'flex-end' : 'flex-start',
                  opacity: msg.optimistic ? 0.65 : 1,
                }}
              >
                {/* Sender label — always shown so every message is attributable */}
                <div
                  style={{
                    fontSize: 11,
                    fontWeight: 600,
                    marginBottom: 2,
                    color: msg.isOwn ? '#0050aa' : color,
                    paddingLeft: 4,
                    paddingRight: 4,
                  }}
                >
                  {msg.isOwn ? 'You' : msg.senderUsername}
                  {msg.optimistic && <span style={{ fontWeight: 400, marginLeft: 4 }}>sending…</span>}
                </div>

                <div
                  style={{
                    background: msg.isOwn ? '#dbeafe' : '#f1f1f1',
                    color: '#111',
                    borderRadius: 12,
                    borderTopRightRadius: msg.isOwn ? 2 : 12,
                    borderTopLeftRadius: msg.isOwn ? 12 : 2,
                    padding: '8px 12px',
                    maxWidth: '70%',
                    wordBreak: 'break-word',
                    position: 'relative',
                    borderLeft: msg.isOwn ? 'none' : `3px solid ${color}`,
                  }}
                >
                  <div>{msg.text ?? '[encrypted]'}</div>

                  {/* Delete only on own messages */}
                  {msg.isOwn && !msg.optimistic && onDeleteMessage && (
                    <button
                      onClick={() => onDeleteMessage(msg.id)}
                      aria-label="Delete this message"
                      style={{
                        position: 'absolute',
                        top: 4,
                        right: 4,
                        background: 'none',
                        border: 'none',
                        cursor: 'pointer',
                        fontSize: 10,
                        color: '#aaa',
                        padding: 2,
                        lineHeight: 1,
                      }}
                    >
                      ✕
                    </button>
                  )}

                  <div style={{ fontSize: 10, opacity: 0.55, marginTop: 4, textAlign: 'right' }}>
                    {new Date(msg.serverTimestamp).toLocaleTimeString()}
                  </div>
                </div>
              </div>
            );
          })
        )}
        <div ref={bottomRef} />
      </div>

      {/* Input area */}
      <div
        style={{
          borderTop: '1px solid #eee',
          padding: '12px 16px',
          display: 'flex',
          gap: 8,
          alignItems: 'flex-end',
        }}
      >
        <div style={{ flex: 1 }}>
          <textarea
            value={inputText}
            onChange={e => { setInputText(e.target.value); setInputError(null); }}
            onKeyDown={handleKeyDown}
            placeholder="Type a message… (Enter to send)"
            rows={2}
            style={{
              width: '100%',
              resize: 'none',
              borderRadius: 8,
              border: inputError ? '1px solid red' : '1px solid #ccc',
              padding: '8px 12px',
              fontFamily: 'inherit',
              fontSize: 14,
              boxSizing: 'border-box',
            }}
            aria-label="Message input"
          />
          {inputError && (
            <div role="alert" style={{ color: 'red', fontSize: 12, marginTop: 2 }}>
              {inputError}
            </div>
          )}
        </div>
        <button
          onClick={handleSend}
          disabled={!inputText.trim()}
          style={{
            padding: '8px 16px',
            borderRadius: 8,
            background: '#0070f3',
            color: '#fff',
            border: 'none',
            cursor: inputText.trim() ? 'pointer' : 'not-allowed',
            opacity: inputText.trim() ? 1 : 0.5,
          }}
          aria-label="Send message"
        >
          Send
        </button>
      </div>
    </div>
  );
}
