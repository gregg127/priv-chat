'use client';

interface EmptyStateProps {
  onCreateRoom: () => void;
}

/**
 * Empty state display for the Room Gateway when no rooms exist.
 * Shows a helpful message and a prominent "Create Room" button.
 */
export default function EmptyState({ onCreateRoom }: EmptyStateProps) {
  return (
    <div
      style={{
        textAlign: 'center',
        padding: '64px 24px',
        border: '2px dashed #ccc',
        borderRadius: 12,
        color: '#666',
      }}
    >
      <p style={{ fontSize: 18, marginBottom: 24 }}>
        No rooms yet — create one to get started
      </p>
      <button
        onClick={onCreateRoom}
        style={{
          padding: '12px 24px',
          fontSize: 16,
          fontWeight: 'bold',
          cursor: 'pointer',
        }}
      >
        Create Room
      </button>
    </div>
  );
}
