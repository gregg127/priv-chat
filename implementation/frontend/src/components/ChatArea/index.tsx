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
}

interface ChatAreaProps {
  roomId: number;
  currentUser: string;
  isOwner: boolean;
  /** Initial messages loaded from REST history endpoint */
  initialMessages: MessageResponse[];
  /** Live messages pushed over WebSocket */
  liveMessages: WsChatMessage[];
  onSend: (text: string) => void;
  onDeleteMessage?: (messageId: number) => void;
}

/**
 * Chat area: renders message history + live messages, input box.
 * Handles empty state (FR-011) and owner delete action (FR-017).
 */
export default function ChatArea({
  roomId,
  currentUser,
  isOwner,
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
      })),
  ].sort((a, b) => a.seq - b.seq);

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
          allMessages.map(msg => (
            <div
              key={msg.clientMessageId}
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: msg.isOwn ? 'flex-end' : 'flex-start',
              }}
            >
              <div
                style={{
                  background: msg.isOwn ? '#0070f3' : '#f1f1f1',
                  color: msg.isOwn ? '#fff' : '#000',
                  borderRadius: 12,
                  padding: '8px 12px',
                  maxWidth: '70%',
                  wordBreak: 'break-word',
                  position: 'relative',
                }}
              >
                {!msg.isOwn && (
                  <div style={{ fontSize: 11, fontWeight: 600, marginBottom: 2, color: '#555' }}>
                    {msg.senderUsername}
                  </div>
                )}
                <div>{msg.text ?? '[encrypted]'}</div>
                {isOwner && onDeleteMessage && (
                  <button
                    onClick={() => onDeleteMessage(msg.id)}
                    aria-label={`Delete message from ${msg.senderUsername}`}
                    style={{
                      position: 'absolute',
                      top: 4,
                      right: 4,
                      background: 'none',
                      border: 'none',
                      cursor: 'pointer',
                      fontSize: 10,
                      color: msg.isOwn ? 'rgba(255,255,255,0.7)' : '#999',
                      padding: 2,
                    }}
                  >
                    ✕
                  </button>
                )}
                <div style={{ fontSize: 10, opacity: 0.6, marginTop: 4, textAlign: 'right' }}>
                  {new Date(msg.serverTimestamp).toLocaleTimeString()}
                </div>
              </div>
            </div>
          ))
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
