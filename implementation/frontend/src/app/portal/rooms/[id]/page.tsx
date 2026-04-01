'use client';

import { useEffect, useRef, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/lib/authContext';
import {
  fetchRoom,
  fetchMessages,
  deleteMessage,
  inviteUser,
  RoomResponse,
  MessageResponse,
  RoomsApiError,
} from '@/lib/roomsApi';
import { WebSocketService, WsChatMessage, WsDeletedMessage } from '@/lib/websocket/WebSocketService';
import { encryptMessage, generateClientMessageId } from '@/lib/signal/signalClient';
import ChatArea from '@/components/ChatArea';
import MemberList from '@/components/MemberList';
import InvitePanel from '@/components/InvitePanel';

/**
 * Room page — shows room details, real-time chat, member list, and owner invite panel.
 *
 * Layout:
 *   ┌─────────────────────────────┬────────────────┐
 *   │  Chat area (flex-grow)      │ Members        │
 *   │                             │ [InvitePanel]  │
 *   └─────────────────────────────┴────────────────┘
 */
export default function RoomPage() {
  const params = useParams<{ id: string }>();
  const roomId = Number(params.id);
  const router = useRouter();
  const { token, username, isRestoring } = useAuth();

  const [room, setRoom] = useState<RoomResponse | null>(null);
  const [history, setHistory] = useState<MessageResponse[]>([]);
  const [liveMessages, setLiveMessages] = useState<WsChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const wsRef = useRef<WebSocketService | null>(null);
  const tokenRef = useRef(token);
  useEffect(() => { tokenRef.current = token; }, [token]);

  // Redirect if not authenticated
  useEffect(() => {
    if (!isRestoring && !token) {
      router.replace('/');
    }
  }, [token, isRestoring, router]);

  // Load room data and message history
  useEffect(() => {
    if (!token || !roomId) return;
    (async () => {
      try {
        const [roomData, messages] = await Promise.all([
          fetchRoom(roomId, token),
          fetchMessages(roomId, token),
        ]);
        setRoom(roomData);
        setHistory(messages);
      } catch (err) {
        if (err instanceof RoomsApiError) {
          if (err.status === 403 || err.status === 404) {
            router.replace('/portal/rooms');
          } else if (err.status === 401) {
            router.replace('/');
          } else {
            setError(err.message);
          }
        }
      } finally {
        setLoading(false);
      }
    })();
  }, [token, roomId, router]);

  // WebSocket connection lifecycle
  useEffect(() => {
    if (!token) return;

    const ws = new WebSocketService(token);
    wsRef.current = ws;
    ws.subscribe(roomId);

    ws.on('message', (msg) => {
      const chatMsg = msg as WsChatMessage;
      if (chatMsg.type === 'message' && chatMsg.roomId === roomId) {
        setLiveMessages(prev => {
          // Replace a pending optimistic entry if clientMessageId matches,
          // otherwise just append (message from another participant).
          const idx = chatMsg.clientMessageId
            ? prev.findIndex(m => m.clientMessageId === chatMsg.clientMessageId && m.optimistic)
            : -1;
          if (idx !== -1) {
            const next = [...prev];
            next[idx] = chatMsg;
            return next;
          }
          return [...prev, chatMsg];
        });
      }
    });

    ws.on('message_deleted', (msg) => {
      const del = msg as WsDeletedMessage;
      if (del.roomId === roomId) {
        setHistory(prev => prev.filter(m => m.id !== del.messageId));
        setLiveMessages(prev => prev.filter(m => m.id !== del.messageId));
      }
    });

    return () => {
      ws.close();
      wsRef.current = null;
    };
  }, [token, roomId]);

  function handleSend(text: string) {
    const ws = wsRef.current;
    if (!ws || !username) return;
    if (!ws.isOpen) {
      setError('Not connected — please wait a moment and try again');
      return;
    }
    const ciphertextB64 = encryptMessage(text, roomId);
    const clientMessageId = generateClientMessageId();

    // Optimistically render the message immediately without waiting for server fanout.
    // Only done when WS is confirmed open — prevents phantom messages if the
    // connection is lost between renders.
    // The server echoes back the confirmed message; on receipt we replace this entry
    // by its clientMessageId so there are no duplicates.
    const optimistic: WsChatMessage = {
      type: 'message',
      roomId,
      id: undefined as unknown as number,
      seq: undefined as unknown as number,
      senderUsername: username,
      ciphertext: ciphertextB64,
      clientMessageId,
      serverTimestamp: new Date().toISOString(),
      optimistic: true,
    };
    setLiveMessages(prev => [...prev, optimistic]);

    ws.sendMessage(roomId, ciphertextB64, clientMessageId);
  }

  async function handleDeleteMessage(messageId: number) {
    if (!token) return;
    try {
      await deleteMessage(roomId, messageId, token);
      setHistory(prev => prev.filter(m => m.id !== messageId));
      setLiveMessages(prev => prev.filter(m => m.id !== messageId));
    } catch (err) {
      if (err instanceof RoomsApiError) {
        setError(err.message);
      }
    }
  }

  async function handleInvite(targetUsername: string): Promise<void> {
    if (!token) return;
    await inviteUser(roomId, targetUsername, token);
    // Refresh room to get updated member list
    const updated = await fetchRoom(roomId, token);
    setRoom(updated);
  }

  if (!token) return null;

  if (isRestoring || loading) {
    return <main style={{ padding: 24 }}><p>Loading room…</p></main>;
  }

  if (error && !room) {
    return (
      <main style={{ padding: 24 }}>
        <p role="alert" style={{ color: 'red' }}>{error}</p>
        <button onClick={() => router.back()}>← Back</button>
      </main>
    );
  }

  if (!room) return null;

  const isOwner = username === room.ownerUsername;

  return (
    <main
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100vh',
        maxHeight: '100vh',
        overflow: 'hidden',
      }}
    >
      {/* Header */}
      <header
        style={{
          padding: '12px 24px',
          borderBottom: '1px solid #eee',
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          flexShrink: 0,
        }}
      >
        <button
          onClick={() => router.push('/portal/rooms')}
          style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 18 }}
          aria-label="Back to rooms"
        >
          ←
        </button>
        <div>
          <h1 style={{ margin: 0, fontSize: 20 }}>{room.name}</h1>
          <p style={{ margin: 0, fontSize: 12, color: '#888' }}>
            {isOwner ? 'You are the owner' : `Owner: ${room.ownerUsername}`}
          </p>
        </div>
      </header>

      {error && (
        <div
          role="alert"
          style={{ padding: '8px 24px', background: '#fee2e2', color: '#dc2626', fontSize: 13 }}
        >
          {error}
        </div>
      )}

      {/* Body */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* Chat */}
        <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
          <ChatArea
            roomId={roomId}
            currentUser={username ?? ''}
            initialMessages={history}
            liveMessages={liveMessages}
            onSend={handleSend}
            onDeleteMessage={handleDeleteMessage}
          />
        </div>

        {/* Sidebar */}
        <aside
          style={{
            width: 240,
            borderLeft: '1px solid #eee',
            padding: '16px',
            overflowY: 'auto',
            flexShrink: 0,
          }}
        >
          <MemberList
            members={room.members ?? []}
            currentUser={username ?? ''}
          />

          {isOwner && (
            <InvitePanel onInvite={handleInvite} />
          )}
        </aside>
      </div>
    </main>
  );
}
