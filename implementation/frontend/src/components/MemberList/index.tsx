'use client';

import type { MemberDto } from '@/lib/roomsApi';

interface MemberListProps {
  members: MemberDto[];
  currentUser: string;
}

/**
 * Displays the list of room members with owner badge.
 */
export default function MemberList({ members, currentUser }: MemberListProps) {
  return (
    <div>
      <h3 style={{ margin: '0 0 12px', fontSize: 14, color: '#666' }}>
        Members ({members.length})
      </h3>
      <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
        {members.map(m => (
          <li
            key={m.username}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '6px 0',
              borderBottom: '1px solid #f0f0f0',
            }}
          >
            <span
              style={{
                width: 32,
                height: 32,
                borderRadius: '50%',
                background: '#e0e0e0',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 14,
                fontWeight: 600,
                flexShrink: 0,
              }}
            >
              {m.username[0].toUpperCase()}
            </span>
            <div>
              <div style={{ fontWeight: m.username === currentUser ? 600 : 400, fontSize: 13 }}>
                {m.username}
                {m.username === currentUser && (
                  <span style={{ fontSize: 11, color: '#888', marginLeft: 4 }}>(you)</span>
                )}
              </div>
              {m.isOwner && (
                <div style={{ fontSize: 11, color: '#0070f3' }}>Owner</div>
              )}
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
