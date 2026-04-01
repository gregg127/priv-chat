'use client';

import { useEffect, useState, useCallback, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/authContext';
import { fetchRooms, createRoom, updateRoom, deleteRoom, refreshToken, RoomResponse, RoomsApiError } from '@/lib/roomsApi';
import RoomCard from '@/components/RoomCard';
import EmptyState from '@/components/EmptyState';

const POLL_INTERVAL_MS = 10_000; // 10 s — intentional: 2 s caused interval-restart memory leak (see git log)

/**
 * Room Gateway page.
 * Shows all public rooms. Supports Create Room, Join, Rename, Delete.
 * Requires a valid JWT in auth context — redirects to / if missing.
 * Polls for new rooms every 10 seconds (FR-003).
 */
export default function RoomsPage() {
  const router = useRouter();
  const { token, setToken, username, isRestoring } = useAuth();
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [capReached, setCapReached] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Refs so polling callback always uses the latest values without
  // recreating the interval on every render/token change.
  const tokenRef = useRef(token);
  const usernameRef = useRef(username);
  const setTokenRef = useRef(setToken);
  const routerRef = useRef(router);
  const isFetching = useRef(false); // prevents concurrent in-flight fetches

  useEffect(() => { tokenRef.current = token; }, [token]);
  useEffect(() => { usernameRef.current = username; }, [username]);
  useEffect(() => { setTokenRef.current = setToken; }, [setToken]);
  useEffect(() => { routerRef.current = router; }, [router]);

  // Redirect only after session-restore attempt is complete
  useEffect(() => {
    if (!isRestoring && !token) {
      router.replace('/');
    }
  }, [token, isRestoring, router]);

  /** Returns a valid (non-expired) token, refreshing silently if < 60 s remain. */
  async function getValidToken(): Promise<string | null> {
    const t = tokenRef.current;
    if (!t) return null;
    try {
      const payload = JSON.parse(atob(t.split('.')[1]));
      const expiresIn = payload.exp * 1000 - Date.now();
      if (expiresIn < 60_000) {
        const newToken = await refreshToken();
        setTokenRef.current(newToken);
        tokenRef.current = newToken;
        return newToken;
      }
    } catch {
      // malformed or refresh failed — use existing token
    }
    return t;
  }

  /** Fetches the room list once. Guards against concurrent calls. */
  const loadRooms = useCallback(async () => {
    if (isFetching.current) return;
    isFetching.current = true;
    try {
      const validToken = await getValidToken();
      if (!validToken) return;

      const data = await fetchRooms(validToken);
      setRooms(data);
      const u = usernameRef.current;
      if (u) {
        setCapReached(data.filter(r => r.creatorUsername === u).length >= 10);
      }
    } catch (err) {
      if (err instanceof RoomsApiError && err.status === 401) {
        routerRef.current.replace('/');
      }
      // Silently ignore transient errors during polling
    } finally {
      isFetching.current = false;
    }
  }, []); // stable — reads all mutable state via refs

  // Initial load: run once when token first becomes available
  const didInitialLoad = useRef(false);
  useEffect(() => {
    if (!token || didInitialLoad.current) return;
    didInitialLoad.current = true;
    loadRooms().finally(() => setLoading(false));
  }, [token, loadRooms]);

  // Polling: stable interval — never restarts due to token/callback churn
  useEffect(() => {
    if (!token) return;
    const id = setInterval(loadRooms, POLL_INTERVAL_MS);
    return () => clearInterval(id);
  }, [token, loadRooms]); // token → only restarts if user logs out/in

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

  if (!token) return null; // Redirect in progress (isRestoring or logged out)

  if (isRestoring || loading) {
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
