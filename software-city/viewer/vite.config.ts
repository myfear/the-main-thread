import { defineConfig } from 'vitest/config';

export default defineConfig({
  base: './',
  publicDir: false,
  server: { host: '127.0.0.1', strictPort: true },
  preview: { host: '127.0.0.1', strictPort: true },
  test: { include: ['tests/unit/**/*.test.ts'] },
});
