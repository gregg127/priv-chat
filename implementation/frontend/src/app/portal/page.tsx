import { headers } from 'next/headers';
import { redirect } from 'next/navigation';

/**
 * Portal interior placeholder page.
 * Server-side session guard: redirects unauthenticated users to the entry gate.
 */
export default async function PortalPage() {
  let username: string | null = null;

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
        username = data.username;
      } else {
        redirect('/');
      }
    } else {
      redirect('/');
    }
  } catch {
    redirect('/');
  }

  return (
    <main>
      <h1>Welcome, {username}!</h1>
      <p>You are inside the network. More features coming soon.</p>
    </main>
  );
}
