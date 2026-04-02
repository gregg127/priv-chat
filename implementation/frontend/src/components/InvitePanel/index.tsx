'use client';

import { useState } from 'react';

interface InvitePanelProps {
  onInvite: (username: string) => Promise<void>;
}

/**
 * Owner-only panel for inviting users to a room by username.
 * FR-014: owner types username → invite sent.
 */
export default function InvitePanel({ onInvite }: InvitePanelProps) {
  const [username, setUsername] = useState('');
  const [status, setStatus] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleInvite() {
    const trimmed = username.trim();
    if (!trimmed) return;

    setLoading(true);
    setStatus(null);
    try {
      await onInvite(trimmed);
      setStatus({ type: 'success', message: `${trimmed} was invited` });
      setUsername('');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to invite user';
      setStatus({ type: 'error', message });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{ marginTop: 24 }}>
      <h3 style={{ margin: '0 0 12px', fontSize: 14, color: '#666' }}>Invite member</h3>
      <div style={{ display: 'flex', gap: 8 }}>
        <input
          type="text"
          value={username}
          onChange={e => { setUsername(e.target.value); setStatus(null); }}
          onKeyDown={e => { if (e.key === 'Enter') handleInvite(); }}
          placeholder="Username"
          disabled={loading}
          style={{
            flex: 1,
            borderRadius: 6,
            border: '1px solid #ccc',
            padding: '6px 10px',
            fontSize: 13,
          }}
          aria-label="Username to invite"
        />
        <button
          onClick={handleInvite}
          disabled={!username.trim() || loading}
          style={{
            borderRadius: 6,
            background: '#0070f3',
            color: '#fff',
            border: 'none',
            padding: '6px 14px',
            cursor: username.trim() && !loading ? 'pointer' : 'not-allowed',
            opacity: username.trim() && !loading ? 1 : 0.5,
            fontSize: 13,
          }}
          aria-label="Send invite"
        >
          {loading ? '…' : 'Invite'}
        </button>
      </div>
      {status && (
        <div
          role="status"
          style={{
            marginTop: 8,
            fontSize: 12,
            color: status.type === 'success' ? 'green' : 'red',
          }}
        >
          {status.message}
        </div>
      )}
    </div>
  );
}
