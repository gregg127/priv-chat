'use client';

import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';

interface AuthContextValue {
  token: string | null;
  username: string | null;
  /** True while the initial session-restore attempt is in-flight. */
  isRestoring: boolean;
  setToken: (token: string | null) => void;
  setUsername: (username: string | null) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/** Extracts the `sub` claim from a JWT without verifying the signature. */
function decodeUsername(token: string): string | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.sub ?? null;
  } catch {
    return null;
  }
}

/**
 * AuthProvider stores the JWT in React memory.
 *
 * On mount it silently calls GET /auth/refresh-token. If the browser still
 * holds a valid Spring Session cookie the server returns a fresh JWT and the
 * user stays logged in across page refreshes without any client-side storage.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(null);
  const [username, setUsername] = useState<string | null>(null);
  const [isRestoring, setIsRestoring] = useState(true);

  const setToken = useCallback((t: string | null) => {
    setTokenState(t);
    if (t) {
      const u = decodeUsername(t);
      if (u) setUsername(u);
    }
  }, []);

  // On mount: restore session from the httpOnly Spring Session cookie.
  useEffect(() => {
    async function tryRestore() {
      try {
        const res = await fetch('/auth/refresh-token', {
          method: 'GET',
          credentials: 'include',
        });
        if (res.ok) {
          const data = await res.json();
          setTokenState(data.token);
          const u = decodeUsername(data.token);
          if (u) setUsername(u);
        }
      } catch {
        // No network or no session — user will be redirected to login by the page.
      } finally {
        setIsRestoring(false);
      }
    }
    tryRestore();
  }, []);

  return (
    <AuthContext.Provider value={{ token, setToken, username, setUsername, isRestoring }}>
      {children}
    </AuthContext.Provider>
  );
}

/**
 * Returns the auth context value. Must be used within an {@link AuthProvider}.
 * Throws if used outside of provider context.
 */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
