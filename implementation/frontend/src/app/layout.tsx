import type { Metadata } from 'next';

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
      <body>{children}</body>
    </html>
  );
}
