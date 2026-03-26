'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import JoinForm from '@/components/JoinForm/JoinForm';
import { joinNetwork } from '@/lib/authApi';

/**
 * Client component: handles localStorage hydration of username
 * and the join network flow.
 */
export default function EntryGateClient() {
  const router = useRouter();
  const [defaultUsername, setDefaultUsername] = useState('');

  // Hydrate username from localStorage after mount (not available server-side)
  useEffect(() => {
    const stored = localStorage.getItem('privchat_username');
    if (stored) {
      setDefaultUsername(stored);
    }
  }, []);

  async function handleSubmit(username: string, password: string) {
    await joinNetwork(username, password);
  }

  function handleSuccess(username: string) {
    localStorage.setItem('privchat_username', username);
    router.push('/portal');
  }

  return (
    <main>
      <h1>Join the Network</h1>
      <JoinForm
        defaultUsername={defaultUsername}
        onSubmit={handleSubmit}
        onSuccess={handleSuccess}
      />
    </main>
  );
}
