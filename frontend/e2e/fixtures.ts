import { expect, test as base } from '@playwright/test';

/**
 * Falla la prueba si el navegador registra una violación de la Content-Security-Policy o un error de
 * consola: contra el stack de Docker, así se comprueba que la CSP estricta no rompe la aplicación.
 */
export const test = base.extend<{ consoleGuard: void }>({
  consoleGuard: [async ({ page }, use) => {
    const problems: string[] = [];
    page.on('console', (message) => {
      const text = message.text();
      // 401 de /auth/me al abrir la app sin sesión y 429/403 provocados a propósito son esperables.
      const expectedHttp = /Failed to load resource: the server responded with a status of (401|403|409|429)/.test(text);
      if ((message.type() === 'error' && !expectedHttp) || /Content Security Policy/i.test(text)) {
        problems.push(`[${message.type()}] ${text}`);
      }
    });
    page.on('pageerror', (error) => problems.push(`[pageerror] ${error.message}`));
    await use();
    expect(problems, 'errores de consola o violaciones de CSP').toEqual([]);
  }, { auto: true }],
});

export { expect };
