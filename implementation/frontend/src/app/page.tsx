import { headers } from 'next/headers';
import { redirect } from 'next/navigation';
import EntryGateClient from './EntryGateClient';

/**
 * Server component: checks session server-side, redirects to /portal if authenticated.
 * Client hydration (localStorage) is handled by the EntryGateClient child component.
 */
export default async function HomePage() {
  // Server-side session check
  try {
    const headersList = await headers();
    const cookie = headersList.get('cookie') ?? '';

    const apiUrl = process.env.API_GATEWAY_URL || 'http://api-gateway:8080';
    const res = await fetch(`${apiUrl}/auth/session`, {
      method: 'GET',
      headers: { cookie },
      cache: 'no-store',
    });

    if (res.ok) {
      const data = await res.json();
      if (data.authenticated === true) {
        redirect('/portal');
      }
    }
  } catch {
    // Session check failed — render the join form
  }

  return <EntryGateClient />;
}
