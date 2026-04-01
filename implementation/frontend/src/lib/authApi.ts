export interface AuthError {
  status: number;
  message: string;
  retryAfterSeconds?: number;
}

export class AuthApiError extends Error {
  status: number;
  retryAfterSeconds?: number;

  constructor(message: string, status: number, retryAfterSeconds?: number) {
    super(message);
    this.name = 'AuthApiError';
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

/**
 * POST /auth/join
 * Joins the network with username + shared password.
 * Returns the username on success; throws AuthApiError on failure.
 */
export async function joinNetwork(
  username: string,
  password: string
): Promise<{ username: string; token?: string }> {
  const response = await fetch('/auth/join', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
    credentials: 'include',
  });

  if (response.ok) {
    const data = await response.json();
    return { username: data.username, token: data.token };
  }

  const retryAfter = response.headers.get('Retry-After');
  const retryAfterSeconds = retryAfter ? parseInt(retryAfter, 10) : undefined;

  let errorMessage = 'An unexpected error occurred';
  try {
    const errorData = await response.json();
    errorMessage = errorData.error || errorMessage;
  } catch {
    // ignore JSON parse error
  }

  throw new AuthApiError(errorMessage, response.status, retryAfterSeconds);
}

/**
 * GET /auth/session
 * Checks whether the current session is authenticated.
 */
export async function checkSession(): Promise<{
  authenticated: boolean;
  username?: string;
}> {
  const response = await fetch('/auth/session', {
    method: 'GET',
    credentials: 'include',
  });

  const data = await response.json();
  return {
    authenticated: data.authenticated === true,
    username: data.username,
  };
}

/**
 * DELETE /auth/session
 * Signs out the current user.
 */
export async function logout(): Promise<void> {
  await fetch('/auth/session', {
    method: 'DELETE',
    credentials: 'include',
  });
}
