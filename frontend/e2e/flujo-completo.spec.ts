import { expect, test } from '@playwright/test';

/**
 * Flujo principal de la sección 5.2: crear proyecto → dibujar red → semáforo y demanda → validar →
 * ejecutar → ver métricas y animación.
 */
test('un usuario dibuja un cruce con semáforo, lo ejecuta y ve la animación', async ({ page }) => {
  const email = `e2e-${Date.now()}@simucity.local`;

  // 1. Crear cuenta
  await page.goto('/login');
  await page.getByRole('tab', { name: 'Crear cuenta' }).click();
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Contraseña').fill('clave-e2e-segura-123');
  await page.getByRole('button', { name: 'Crear cuenta y entrar' }).click();
  await expect(page).toHaveURL(/\/projects$/);

  // 2. Proyecto y escenario
  await page.getByLabel('Nombre del proyecto').fill('Prueba E2E');
  await page.getByRole('button', { name: 'Crear proyecto' }).click();
  await page.getByRole('link', { name: 'Prueba E2E' }).click();
  await page.getByLabel('Nombre del escenario').fill('Cruce con semáforo');
  await page.getByRole('button', { name: 'Crear y abrir editor' }).click();
  await expect(page).toHaveURL(/\/scenarios\//);

  // 3. Dibujar: dos entradas (oeste y sur), una intersección y dos salidas (este y norte)
  const pane = page.locator('.react-flow__pane');
  const place = async (tool: string, x: number, y: number) => {
    await page.getByRole('button', { name: tool }).click();
    await pane.click({ position: { x, y } });
  };
  await place('+ Entrada', 150, 400);       // E1 oeste
  await place('+ Entrada', 450, 700);       // E2 sur
  await place('+ Intersección', 450, 400);  // I1
  await place('+ Salida', 750, 400);        // S1 este
  await place('+ Salida', 450, 100);        // S2 norte
  await expect(page.locator('.road-node')).toHaveCount(5);
  // Cada nodo queda donde se hizo clic: el lienzo no cambia el zoom mientras se dibuja.
  const e1 = await page.locator('[title="Entrada E1"]').boundingBox();
  const i1 = await page.locator('[title="Intersección I1"]').boundingBox();
  expect(Math.round(i1!.x - e1!.x)).toBe(300);

  // 4. Conectar calles de un solo sentido hacia el cruce y desde el cruce
  await page.getByRole('button', { name: 'Conectar calle' }).click();
  await page.getByLabel('Doble sentido').uncheck();
  const node = (title: string) => page.locator(`[title="${title}"]`);
  for (const [from, to] of [['Entrada E1', 'Intersección I1'], ['Intersección I1', 'Salida S1'],
    ['Entrada E2', 'Intersección I1'], ['Intersección I1', 'Salida S2']]) {
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: 'Conectar calle' }).click();
    await node(from!).click();
    await node(to!).click();
  }
  await expect(page.locator('.react-flow__edge')).toHaveCount(4);

  // 5. Semáforo en el cruce
  await page.keyboard.press('Escape');
  await node('Intersección I1').click();
  await page.getByLabel(/Controlar con semáforo/).check();
  await expect(page.getByLabel(/Verde por acceso/)).toHaveValue('30');

  // 6. Demanda: oeste → este y sur → norte
  for (const [from, to] of [['E1', 'S1'], ['E2', 'S2']]) {
    await page.getByLabel('Desde').selectOption(from!);
    await page.getByLabel('Hacia').selectOption(to!);
    await page.getByRole('button', { name: 'Añadir', exact: true }).click();
  }
  await expect(page.locator('.demand-item')).toHaveCount(2);

  // 7. Validar (guarda antes)
  await page.getByRole('button', { name: 'Validar' }).click();
  await expect(page.getByText('El escenario es válido.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Guardado' })).toBeVisible();

  // 8. Ejecutar 10 minutos con semilla fija
  const duration = page.getByLabel(/Duración/);
  await duration.fill('10');
  await duration.press('Enter');
  await page.getByLabel(/Semilla/).fill('42');
  await page.getByRole('button', { name: 'Ejecutar' }).click();
  await expect(page.getByText('Completada')).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole('row', { name: /Viajes completados/ })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Descargar métricas (CSV)' })).toBeVisible();

  // 9. Animación sobre el lienzo, controlable con la línea de tiempo
  await expect(page.locator('canvas.playback-layer')).toBeVisible();
  const slider = page.getByLabel('Tiempo simulado');
  await page.getByRole('button', { name: 'Pausa' }).click();
  await slider.fill('300');
  // 10 min con una muestra cada 5 s desde el segundo 0: la última es la del segundo 595.
  await expect(page.getByText('05:00 / 09:55')).toBeVisible();

  await page.screenshot({ path: test.info().outputPath('editor-con-animacion.png') });

  // 10. Al recargar, el escenario guardado sigue ahí
  await page.reload();
  await expect(page.locator('.road-node')).toHaveCount(5);
  await expect(page.locator('.react-flow__edge')).toHaveCount(4);
});
