import { defineConfig, devices } from '@playwright/test';

/**
 * Pruebas de extremo a extremo: navegador real + frontend + backend + PostgreSQL.
 *
 * - Por defecto (desarrollo): levanta Vite y el jar del backend si no están en marcha. Requiere
 *   `docker compose -f docker/compose.yaml up -d` y el jar construido (`cd backend && ./mvnw -DskipTests install`).
 * - Contra el stack de Docker (nginx + CSP + cabeceras reales): `E2E_BASE_URL=http://localhost:8081 npm run e2e`.
 */
const externalBaseUrl = process.env.E2E_BASE_URL;

export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  reporter: [['list']],
  use: {
    baseURL: externalBaseUrl ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1400, height: 900 } } }],
  webServer: externalBaseUrl ? undefined : [
    {
      command: 'java -jar ../backend/app/target/simucity-app-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev',
      url: 'http://localhost:8088/actuator/health',
      reuseExistingServer: true,
      timeout: 120_000,
    },
    {
      command: 'npm run dev',
      url: 'http://localhost:5173',
      reuseExistingServer: true,
      timeout: 60_000,
    },
  ],
});
