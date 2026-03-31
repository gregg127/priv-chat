'use client';

import { useState } from 'react';
import { AuthApiError } from '@/lib/authApi';

export interface JoinFormProps {
  defaultUsername?: string;
  onSuccess: (username: string, token?: string) => void;
  onSubmit: (username: string, password: string) => Promise<{ username: string; token?: string }>;
}

export default function JoinForm({ defaultUsername = '', onSuccess, onSubmit }: JoinFormProps) {
  const [username, setUsername] = useState(defaultUsername);
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    // Client-side validation
    const trimmedUsername = username.trim();
    if (!trimmedUsername) {
      setError('Username is required');
      return;
    }
    if (trimmedUsername.length > 64) {
      setError('Username must be 64 characters or fewer');
      return;
    }
    if (!password) {
      setError('Password is required');
      return;
    }

    setLoading(true);
    try {
      const result = await onSubmit(trimmedUsername, password);
      onSuccess(trimmedUsername, result?.token);
    } catch (err) {
      if (err instanceof AuthApiError) {
        if (err.status === 429 && err.retryAfterSeconds !== undefined) {
          const minutes = Math.ceil(err.retryAfterSeconds / 60);
          setError(`Too many attempts — try again in ${minutes} minutes`);
        } else {
          setError(err.message);
        }
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('An unexpected error occurred');
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} aria-label="Join network form">
      <div>
        <label htmlFor="username">Username</label>
        <input
          id="username"
          type="text"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          maxLength={64}
          disabled={loading}
          autoComplete="username"
          placeholder="Choose a display name"
        />
      </div>

      <div>
        <label htmlFor="password">Network Password</label>
        <input
          id="password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          disabled={loading}
          autoComplete="current-password"
          placeholder="Enter network password"
        />
      </div>

      {error && (
        <p role="alert" aria-live="polite" style={{ color: 'red' }}>
          {error}
        </p>
      )}

      <button type="submit" disabled={loading} aria-busy={loading}>
        {loading ? (
          <>
            <span aria-hidden="true">⏳</span> Joining…
          </>
        ) : (
          'Join network'
        )}
      </button>
    </form>
  );
}
