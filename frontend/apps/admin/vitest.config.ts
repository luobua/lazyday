import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname),
      '@lazyday/types': path.resolve(__dirname, '../../packages/types/src'),
      '@lazyday/utils': path.resolve(__dirname, '../../packages/utils/src'),
      '@lazyday/api-client': path.resolve(__dirname, '../../packages/api-client/src'),
      '@lazyday/ui': path.resolve(__dirname, '../../packages/ui/src'),
    },
  },
});
