'use client';

import { createContext, useContext, useState, ReactNode } from 'react';

interface AuthContextValue {
  token: string | null;
  username: string | null;
  setToken: (token: string | null) => void;
  setUsername: (username: string | null) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * AuthProvider stores the JWT token and username in React memory (NOT localStorage).
 * Wrap your app layout with this provider so all pages can access auth state.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [username, setUsername] = useState<string | null>(null);

  return (
    <AuthContext.Provider value={{ token, setToken, username, setUsername }}>
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
