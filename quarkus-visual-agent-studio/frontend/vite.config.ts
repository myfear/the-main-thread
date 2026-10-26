import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Only the public Studio API is proxied. Internal executor and Runner routes
// remain outside the browser application's proxy surface.
const proxy = { '/api': { target: process.env.STUDIO_BACKEND_URL ?? 'http://127.0.0.1:8126' } };
export default defineConfig({
  plugins: [react()],
  server: { host: '127.0.0.1', port: 5178, strictPort: true, proxy },
  preview: { host: '127.0.0.1', port: 5178, strictPort: true, proxy },
  test: { include: ['src/**/*.test.ts'] },
});
