import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Mismo origen para el navegador: la cookie de sesión y el CSRF funcionan sin configurar CORS.
    proxy: {
      '/api': { target: process.env.SIMUCITY_API_URL ?? 'http://localhost:8088', changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
  },
});
