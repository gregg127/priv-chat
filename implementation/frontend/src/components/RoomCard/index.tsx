'use client';

import { RoomResponse } from '@/lib/roomsApi';

interface RoomCardProps {
  room: RoomResponse;
  currentUser: string | null;
  onJoin: (roomId: number) => void;
  onRename?: (roomId: number, newName: string) => void;
  onDelete?: (roomId: number) => void;
}

/**
 * Displays a room card with name, creator, creation time, and active member count.
 * Shows Rename and Delete buttons only when the current user is the room owner.
 */
export default function RoomCard({ room, currentUser, onJoin, onRename, onDelete }: RoomCardProps) {
  const isOwner = currentUser === room.ownerUsername;

  function handleRenameClick() {
    if (!onRename) return;
    const newName = prompt('Enter new room name:', room.name);
    if (newName && newName.trim() && newName.trim() !== room.name) {
      onRename(room.id, newName.trim());
    }
  }

  function handleDeleteClick() {
    if (!onDelete) return;
    if (confirm('Delete this room?')) {
      onDelete(room.id);
    }
  }

  const formattedDate = new Date(room.createdAt).toLocaleString();

  return (
    <div style={{ border: '1px solid #ccc', borderRadius: 8, padding: 16, marginBottom: 12 }}>
      <h3 style={{ margin: '0 0 8px' }}>{room.name}</h3>
      <p style={{ margin: '0 0 4px', color: '#666' }}>
        Created by <strong>{room.creatorUsername}</strong> · {formattedDate}
      </p>
      <p style={{ margin: '0 0 12px', color: '#888' }}>
        Active members: {room.activeMemberCount}
      </p>
      <div style={{ display: 'flex', gap: 8 }}>
        <button onClick={() => onJoin(room.id)}>Join</button>
        {isOwner && onRename && (
          <button onClick={handleRenameClick}>Rename</button>
        )}
        {isOwner && onDelete && (
          <button onClick={handleDeleteClick} style={{ color: 'red' }}>Delete</button>
        )}
      </div>
    </div>
  );
}
