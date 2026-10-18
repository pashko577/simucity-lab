import { describe, expect, it, vi } from 'vitest';

import { ApiClient, ApiError, readCookie } from './client';

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const CSRF_BODY = { headerName: 'X-XSRF-TOKEN', cookieName: 'XSRF-TOKEN' };

/** Simula el almacén de cookies del navegador: GET /auth/csrf "escribe" la cookie. */
function browser(responses: Response[], initialCookie = '') {
  let cookie = initialCookie;
  const fetchFn = vi.fn(async (url: string | URL | Request, _init?: RequestInit) => {
    if (String(url).endsWith('/auth/csrf')) {
      cookie = `JSESSIONID=x; XSRF-TOKEN=${cookie.includes('tok-1') ? 'tok-2' : 'tok-1'}`;
      return json(200, CSRF_BODY);
    }
    return responses.shift()!;
  });
  return { fetchFn, client: new ApiClient(fetchFn as typeof fetch, () => cookie) };
}

const headerOf = (init: unknown) => ((init as RequestInit).headers as Record<string, string>)['X-XSRF-TOKEN'];

describe('readCookie', () => {
  it('lee una cookie entre varias y decodifica su valor', () => {
    expect(readCookie('XSRF-TOKEN', 'a=1; XSRF-TOKEN=abc%3D%3D; b=2')).toBe('abc==');
    expect(readCookie('XSRF-TOKEN', 'a=1')).toBeNull();
  });
});

describe('ApiClient', () => {
  it('no pide token CSRF en un GET', async () => {
    const { fetchFn, client } = browser([json(200, { id: '1' })]);

    await client.get('/projects/1');

    expect(fetchFn).toHaveBeenCalledTimes(1);
    expect(fetchFn.mock.calls[0]![0]).toBe('/api/v1/projects/1');
  });

  it('copia el valor de la cookie en la cabecera; solo pide /auth/csrf si la cookie no existe', async () => {
    const { fetchFn, client } = browser([json(201, { id: 'a' }), json(201, { id: 'b' })]);

    await client.post('/projects', { name: 'A' });
    await client.post('/projects', { name: 'B' });

    expect(fetchFn).toHaveBeenCalledTimes(3); // un solo GET /auth/csrf
    expect(headerOf(fetchFn.mock.calls[1]![1])).toBe('tok-1');
    expect(headerOf(fetchFn.mock.calls[2]![1])).toBe('tok-1');
    expect((fetchFn.mock.calls[1]![1] as RequestInit).credentials).toBe('same-origin');
  });

  it('si la cookie ya existe no hace la petición extra', async () => {
    const { fetchFn, client } = browser([json(201, { id: 'a' })], 'XSRF-TOKEN=ya-existe');

    await client.post('/projects', { name: 'A' });

    expect(fetchFn).toHaveBeenCalledTimes(1);
    expect(headerOf(fetchFn.mock.calls[0]![1])).toBe('ya-existe');
  });

  it('ante un 403 renueva el token y reintenta una sola vez', async () => {
    const { fetchFn, client } = browser([json(403, {}), json(201, { id: 'a' })], 'XSRF-TOKEN=tok-1');

    await expect(client.post('/projects', { name: 'A' })).resolves.toEqual({ id: 'a' });
    expect(headerOf(fetchFn.mock.calls[2]![1])).toBe('tok-2');
  });

  it('convierte Problem Details en ApiError con errores de campo e issues', async () => {
    const { client } = browser([json(400, {
      detail: 'El escenario no se puede ejecutar: tiene errores.',
      errors: { name: 'no puede estar vacío' },
      issues: [{ severity: 'ERROR', code: 'EDGE_INVALID_LENGTH', message: 'Longitud inválida', elementId: 'e1' }],
    })], 'XSRF-TOKEN=t');

    const error = await client.post('/scenarios/1/runs', {}).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.message).toBe('El escenario no se puede ejecutar: tiene errores.');
    expect(apiError.fieldErrors.name).toBe('no puede estar vacío');
    expect(apiError.issues[0]?.code).toBe('EDGE_INVALID_LENGTH');
  });

  it('usa un mensaje genérico en español si no hay cuerpo', async () => {
    const { client } = browser([new Response(null, { status: 401 })]);

    await expect(client.get('/auth/me')).rejects.toThrow('Tu sesión no es válida. Inicia sesión de nuevo.');
  });

  it('devuelve undefined en un 204', async () => {
    const { client } = browser([new Response(null, { status: 204 })], 'XSRF-TOKEN=t');

    await expect(client.delete('/projects/1')).resolves.toBeUndefined();
  });
});
