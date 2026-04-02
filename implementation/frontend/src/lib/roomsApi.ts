export interface MemberDto {
  username: string;
  joinedAt: string;
  isOwner: boolean;
}

export interface RoomResponse {
  id: number;
  name: string;
  creatorUsername: string;
  ownerUsername: string;
  createdAt: string;
  activeMemberCount: number;
  members?: MemberDto[];
}

export interface MessageResponse {
  id: number;
  seq: number;
  senderUsername: string;
  ciphertext: string;
  clientMessageId: string;
  serverTimestamp: string;
}

export interface InviteRequest {
  username: string;
}

export interface CreateRoomRequest {
  name?: string | null;
}

export interface UpdateRoomRequest {
  name: string;
}

export class RoomsApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.name = 'RoomsApiError';
    this.status = status;
  }
}

/**
 * Calls a rooms API endpoint, injecting the Authorization: Bearer header.
 * Throws RoomsApiError on non-2xx responses.
 */
async function roomsFetch<T>(
  url: string,
  token: string,
  options: RequestInit = {}
): Promise<T> {
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...(options.headers || {}),
    },
    credentials: 'include',
  });

  if (response.status === 204) {
    return undefined as unknown as T;
  }

  const data = await response.json();

  if (!response.ok) {
    throw new RoomsApiError(data.error || 'Rooms API error', response.status);
  }

  return data as T;
}

/**
 * GET /rooms — list all rooms (newest first)
 */
export async function fetchRooms(token: string): Promise<RoomResponse[]> {
  return roomsFetch<RoomResponse[]>('/rooms', token);
}

/**
 * GET /rooms/{id} — fetch a single room with members
 */
export async function fetchRoom(id: number, token: string): Promise<RoomResponse> {
  return roomsFetch<RoomResponse>(`/rooms/${id}`, token);
}

/**
 * GET /rooms/{id}/messages — paginated history
 */
export async function fetchMessages(
  roomId: number,
  token: string,
  beforeSeq = 0,
  limit = 50
): Promise<MessageResponse[]> {
  const params = new URLSearchParams();
  if (beforeSeq > 0) params.set('beforeSeq', String(beforeSeq));
  if (limit !== 50) params.set('limit', String(limit));
  const qs = params.toString() ? `?${params}` : '';
  return roomsFetch<MessageResponse[]>(`/rooms/${roomId}/messages${qs}`, token);
}

/**
 * DELETE /rooms/{id}/messages/{messageId} — soft-delete a message (owner only)
 */
export async function deleteMessage(
  roomId: number,
  messageId: number,
  token: string
): Promise<void> {
  return roomsFetch<void>(`/rooms/${roomId}/messages/${messageId}`, token, {
    method: 'DELETE',
  });
}

/**
 * POST /rooms/{id}/invites — invite a user to the room (owner only)
 */
export async function inviteUser(
  roomId: number,
  username: string,
  token: string
): Promise<{ username: string; joinedAt: string; joinSeq: number }> {
  return roomsFetch<{ username: string; joinedAt: string; joinSeq: number }>(
    `/rooms/${roomId}/invites`,
    token,
    {
      method: 'POST',
      body: JSON.stringify({ username }),
    }
  );
}

/**
 * POST /rooms — create a new room
 * If name is omitted, the server generates one as `{username}-room-{n}`.
 */
export async function createRoom(token: string, name?: string | null): Promise<RoomResponse> {
  return roomsFetch<RoomResponse>('/rooms', token, {
    method: 'POST',
    body: JSON.stringify(name !== undefined && name !== null ? { name } : {}),
  });
}

/**
 * PUT /rooms/{id} — rename a room (creator only)
 */
export async function updateRoom(id: number, name: string, token: string): Promise<RoomResponse> {
  return roomsFetch<RoomResponse>(`/rooms/${id}`, token, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  });
}

/**
 * DELETE /rooms/{id} — delete a room (creator only)
 */
export async function deleteRoom(id: number, token: string): Promise<void> {
  return roomsFetch<void>(`/rooms/${id}`, token, {
    method: 'DELETE',
  });
}

/**
 * GET /auth/refresh-token — get a fresh JWT using the current session cookie
 */
export async function refreshToken(): Promise<string> {
  const response = await fetch('/auth/refresh-token', {
    method: 'GET',
    credentials: 'include',
  });
  if (!response.ok) {
    throw new RoomsApiError('Authentication required', response.status);
  }
  const data = await response.json();
  return data.token;
}
