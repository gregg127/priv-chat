'use client';

import { useEffect, useState, useCallback, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/authContext';
import { fetchRooms, createRoom, updateRoom, deleteRoom, refreshToken, RoomResponse, RoomsApiError } from '@/lib/roomsApi';
import RoomCard from '@/components/RoomCard';
import EmptyState from '@/components/EmptyState';

/**
 * Room Gateway page.
 * Shows all public rooms. Supports Create Room, Join, Rename, Delete.
 * Requires a valid JWT in auth context — redirects to / if missing.
 * Polls for new rooms every 2 seconds (FR-003, SC-003).
 */
export default function RoomsPage() {
  const router = useRouter();
  const { token, setToken, username } = useAuth();
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [capReached, setCapReached] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Redirect if unauthenticated
  useEffect(() => {
    if (!token) {
      router.replace('/');
    }
  }, [token, router]);

  /**
   * Gets a fresh token if needed (< 60s remaining) and retries on 401.
   */
  const getValidToken = useCallback(async (): Promise<string | null> => {
    if (!token) return null;

    try {
      // Decode payload to check expiry
      const payload = JSON.parse(atob(token.split('.')[1]));
      const expiresIn = payload.exp * 1000 - Date.now();
      if (expiresIn < 60_000) {
        const newToken = await refreshToken();
        setToken(newToken);
        return newToken;
      }
    } catch {
      // Token malformed or refresh failed — use existing
    }
    return token;
  }, [token, setToken]);

  const loadRooms = useCallback(async () => {
    const validToken = await getValidToken();
    if (!validToken) return;

    try {
      const data = await fetchRooms(validToken);
      setRooms(data);
      // Infer cap: count rooms where current user is creator
      if (username) {
        const myRooms = data.filter(r => r.creatorUsername === username);
        setCapReached(myRooms.length >= 10);
      }
    } catch (err) {
      if (err instanceof RoomsApiError && err.status === 401) {
        router.replace('/');
      }
      // Ignore other errors during polling
    }
  }, [getValidToken, username, router]);

  // Initial load
  useEffect(() => {
    if (!token) return;
    loadRooms().finally(() => setLoading(false));
  }, [token, loadRooms]);

  // Poll every 2 seconds (FR-003, SC-003)
  useEffect(() => {
    if (!token) return;
    pollingRef.current = setInterval(() => {
      loadRooms();
    }, 2000);
    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
  }, [token, loadRooms]);

  async function handleCreateRoom() {
    const validToken = await getValidToken();
    if (!validToken) return;

    try {
      const newRoom = await createRoom(validToken);
      setRooms(prev => [newRoom, ...prev]);
      router.push(`/portal/rooms/${newRoom.id}`);
    } catch (err) {
      if (err instanceof RoomsApiError) {
        if (err.status === 422) {
          setCapReached(true);
        } else {
          setError(err.message);
        }
      }
    }
  }

  function handleJoin(roomId: number) {
    router.push(`/portal/rooms/${roomId}`);
  }

  async function handleRename(roomId: number, newName: string) {
    const validToken = await getValidToken();
    if (!validToken) return;

    try {
      const updated = await updateRoom(roomId, newName, validToken);
      setRooms(prev => prev.map(r => r.id === roomId ? updated : r));
    } catch (err) {
      if (err instanceof RoomsApiError) {
        if (err.status === 409) {
          setError('Name already taken');
        } else {
          setError(err.message);
        }
      }
    }
  }

  async function handleDelete(roomId: number) {
    const validToken = await getValidToken();
    if (!validToken) return;

    try {
      await deleteRoom(roomId, validToken);
      setRooms(prev => {
        const updated = prev.filter(r => r.id !== roomId);
        if (username) {
          const myRooms = updated.filter(r => r.creatorUsername === username);
          setCapReached(myRooms.length >= 10);
        }
        return updated;
      });
    } catch (err) {
      if (err instanceof RoomsApiError) {
        setError(err.message);
      }
    }
  }

  if (!token) return null; // Redirect in progress

  if (loading) {
    return <main><p>Loading rooms…</p></main>;
  }

  return (
    <main style={{ maxWidth: 800, margin: '0 auto', padding: 24 }}>
      <h1>Room Gateway</h1>

      {error && (
        <p role="alert" style={{ color: 'red' }}>{error}</p>
      )}

      <div style={{ marginBottom: 16 }}>
        <button
          onClick={handleCreateRoom}
          disabled={capReached}
          title={capReached ? 'You have reached the 10-room limit' : undefined}
        >
          Create Room
        </button>
        {capReached && (
          <span style={{ marginLeft: 8, color: '#888' }}>
            Room limit reached (10 max)
          </span>
        )}
      </div>

      {rooms.length === 0 ? (
        <EmptyState onCreateRoom={handleCreateRoom} />
      ) : (
        <div>
          {rooms.map(room => (
            <RoomCard
              key={room.id}
              room={room}
              currentUser={username}
              onJoin={handleJoin}
              onRename={handleRename}
              onDelete={handleDelete}
            />
          ))}
        </div>
      )}
    </main>
  );
}
