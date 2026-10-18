import type { Page } from '@playwright/test';
import { expect, test } from './fixtures';

/** Llamada a la API con la sesión del navegador y el token CSRF de su cookie. */
async function api<T>(page: Page, method: string, path: string, data?: unknown): Promise<T> {
  const xsrf = async () => (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value;
  if (!(await xsrf())) {
    await page.request.get('/api/v1/auth/csrf');
  }
  const response = await page.request.fetch(`/api/v1${path}`, {
    method,
    data,
    headers: { 'X-XSRF-TOKEN': (await xsrf())! },
  });
  expect(response.ok(), `${method} ${path} → ${response.status()}`).toBeTruthy();
  return (response.status() === 204 ? undefined : await response.json()) as T;
}

async function runAndWait(page: Page, scenarioId: string): Promise<string> {
  const run = await api<{ id: string }>(page, 'POST', `/scenarios/${scenarioId}/runs`, { seed: 7, durationSeconds: 900 });
  await expect.poll(async () => (await api<{ status: string }>(page, 'GET', `/runs/${run.id}`)).status,
    { timeout: 30_000 }).toBe('COMPLETED');
  return run.id;
}

/** Calle saturada: 40 veh/min frente a 30 veh/min de capacidad con un carril. */
function road(lanes: number, capacity: number) {
  const edge = (id: string, from: string, to: string, length: number) => ({
    id, from, to, lengthMeters: length, lanes, speedLimitMps: 10, storageCapacity: capacity, closed: false,
  });
  return {
    nodes: [
      { id: 'E', type: 'ENTRY', x: 0, y: 0 },
      { id: 'I', type: 'INTERSECTION', x: 100, y: 0 },
      { id: 'X', type: 'EXIT', x: 300, y: 0 },
    ],
    edges: [edge('e1', 'E', 'I', 100), edge('e2', 'I', 'X', 200)],
    demands: [{ id: 'd1', origin: 'E', destination: 'X', vehiclesPerMinute: 40, mode: 'POISSON' }],
    signals: [],
    prohibitedMovements: [],
  };
}

async function batchAndWait(page: Page, scenarioId: string, size: number, seed: number): Promise<string> {
  const batch = await api<{ id: string }>(page, 'POST', `/scenarios/${scenarioId}/batches`,
    { size, seed, durationSeconds: 900 });
  await expect.poll(async () => (await api<{ status: string }>(page, 'GET', `/batches/${batch.id}`)).status,
    { timeout: 60_000 }).toBe('COMPLETED');
  return batch.id;
}

test('lotes de varias semillas: lanzar desde el editor y comparar con intervalo de confianza', async ({ page }) => {
  const email = `lote-${Date.now()}@simucity.local`;
  await api(page, 'POST', '/auth/register', { email, password: 'clave-e2e-segura-123' });
  await api(page, 'POST', '/auth/login', { email, password: 'clave-e2e-segura-123' });
  const project = await api<{ id: string }>(page, 'POST', '/projects', { name: 'Lotes E2E' });
  const base = await api<{ id: string }>(page, 'POST', `/projects/${project.id}/scenarios`,
    { name: 'Actual', definition: road(1, 10) });
  const wide = await api<{ id: string }>(page, 'POST', `/projects/${project.id}/scenarios`,
    { name: 'Ampliada', definition: road(2, 20) });

  // A: desde el editor, con "Repeticiones"
  await page.goto(`/scenarios/${base.id}`);
  const duration = page.getByLabel(/Duración/);
  await duration.fill('15');
  await duration.press('Enter');
  const repetitions = page.getByLabel(/Repeticiones/);
  await repetitions.fill('5');
  await repetitions.press('Enter');
  await page.getByLabel(/Semilla inicial/).fill('42');
  await page.getByRole('button', { name: 'Ejecutar' }).click();
  await expect(page.getByText('Completado', { exact: true })).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText('Media ± desviación típica entre semillas')).toBeVisible();
  await expect(page.getByText(/5\/5 ejecuciones · semillas 42–46/)).toBeVisible();

  // B: por la API, mismas semillas
  const wideBatch = await batchAndWait(page, wide.id, 5, 42);

  await page.getByRole('link', { name: 'Comparar con otro lote' }).click();
  await expect(page.getByRole('tab', { name: 'Lotes (varias semillas)' })).toHaveAttribute('aria-selected', 'true');
  const pickB = page.getByLabel('B · Alternativa');
  await expect(pickB.locator(`option[value="${wideBatch}"]`)).toHaveText(/^Ampliada · 5 semillas \(42–46\)/);
  await pickB.selectOption(wideBatch);

  await expect(page.getByText('✓ Comparación justa:')).toBeVisible();
  await expect(page.getByText(/Comparación pareada: 5 semillas/)).toBeVisible();
  const travel = page.getByRole('row', { name: /Tiempo medio de viaje/ });
  await expect(travel).toContainText('Mejora');
  await expect(travel).toContainText(/\[−\d/); // intervalo con extremos negativos
  await expect(page.getByRole('row', { name: /Vehículos generados/ })).toContainText('Sin cambios');

  await page.screenshot({ path: test.info().outputPath('comparador-lotes.png'), fullPage: true });
});

test('comparar una calle saturada con la misma calle ampliada', async ({ page }) => {
  const email = `cmp-${Date.now()}@simucity.local`;
  await api(page, 'POST', '/auth/register', { email, password: 'clave-e2e-segura-123' });
  await api(page, 'POST', '/auth/login', { email, password: 'clave-e2e-segura-123' });

  const project = await api<{ id: string }>(page, 'POST', '/projects', { name: 'Comparación E2E' });
  const base = await api<{ id: string }>(page, 'POST', `/projects/${project.id}/scenarios`,
    { name: 'Actual', definition: road(1, 10) });
  const copy = await api<{ id: string }>(page, 'POST', `/scenarios/${base.id}/duplicate`, { name: 'Ampliada' });
  await api(page, 'PUT', `/scenarios/${copy.id}`, { name: 'Ampliada', definition: road(2, 20) });
  const runA = await runAndWait(page, base.id);
  const runB = await runAndWait(page, copy.id);

  // Desde la página del proyecto
  await page.goto(`/projects/${project.id}`);
  await page.getByRole('link', { name: 'Comparar ejecuciones' }).click();
  await page.getByLabel('A · Base').selectOption(runA);
  await page.getByLabel('B · Alternativa').selectOption(runB);

  await expect(page.getByText('✓ Comparación justa:')).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`base=${runA}.*candidate=${runB}`));
  await expect(page.getByText('Calle e1: carriles: 1 → 2; capacidad: 10 veh → 20 veh')).toBeVisible();

  const travelTime = page.locator('figure.metric-bars', { hasText: 'Tiempo medio de viaje' });
  await expect(travelTime).toContainText('Mejora');
  await expect(page.getByRole('row', { name: /Vehículos generados/ })).toContainText('Sin cambios');
  await expect(page.getByRole('heading', { name: 'Calles donde más cambió la cola' })).toBeVisible();

  await page.screenshot({ path: test.info().outputPath('comparador.png'), fullPage: true });

  // Intercambiar A y B invierte la evaluación
  await page.getByRole('button', { name: 'Intercambiar A y B' }).click();
  await expect(travelTime).toContainText('Empeora');
});
