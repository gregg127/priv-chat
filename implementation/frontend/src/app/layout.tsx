import type { Metadata } from 'next';
import { AuthProvider } from '@/lib/authContext';

export const metadata: Metadata = {
  title: 'PrivChat',
  description: 'Private network chat',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
