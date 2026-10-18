import { defineConfig, devices } from '@playwright/test';

/**
 * Pruebas de extremo a extremo: navegador real + frontend (Vite) + backend (Spring Boot) + PostgreSQL (Docker).
 * Requisitos: `docker compose -f docker/compose.yaml up -d` y el jar del backend construido
 * (`cd backend && ./mvnw -DskipTests install`).
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1400, height: 900 } } }],
  webServer: [
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
