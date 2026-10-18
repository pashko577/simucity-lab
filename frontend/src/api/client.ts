import type { ValidationIssue } from './types';

/** Error de la API con los datos de Problem Details (RFC 9457) que envía el backend. */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;
  readonly issues: ValidationIssue[];

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}, issues: ValidationIssue[] = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.issues = issues;
  }
}

const MUTATING = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

export function readCookie(name: string, cookieString: string = document.cookie): string | null {
  for (const part of cookieString.split(';')) {
    const [key, ...value] = part.trim().split('=');
    if (key === name) {
      return decodeURIComponent(value.join('='));
    }
  }
  return null;
}

/**
 * Cliente HTTP de la API. La sesión es una cookie HttpOnly que gestiona el navegador. Para CSRF se copia el
 * valor de la cookie XSRF-TOKEN (legible a propósito) en la cabecera X-XSRF-TOKEN de cada mutación; si aún
 * no existe, se pide a GET /auth/csrf. No se guarda nada en localStorage.
 */
export class ApiClient {
  private readonly fetchFn: typeof fetch;
  private readonly cookies: () => string;

  constructor(fetchFn: typeof fetch = (...args) => fetch(...args), cookies: () => string = () => document.cookie) {
    this.fetchFn = fetchFn;
    this.cookies = cookies;
  }

  get<T>(path: string): Promise<T> {
    return this.request<T>('GET', path);
  }

  post<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>('POST', path, body);
  }

  put<T>(path: string, body: unknown): Promise<T> {
    return this.request<T>('PUT', path, body);
  }

  patch<T>(path: string, body: unknown): Promise<T> {
    return this.request<T>('PATCH', path, body);
  }

  delete(path: string): Promise<void> {
    return this.request<void>('DELETE', path);
  }

  /** Respuesta en texto (p. ej. CSV). */
  async getText(path: string): Promise<string> {
    const response = await this.fetchFn(`/api/v1${path}`, { credentials: 'same-origin' });
    if (!response.ok) {
      throw await toApiError(response);
    }
    return response.text();
  }

  private async request<T>(method: string, path: string, body?: unknown, retried = false): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    if (MUTATING.has(method)) {
      headers[CSRF_HEADER] = await this.csrfToken(retried);
    }

    const response = await this.fetchFn(`/api/v1${path}`, {
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : JSON.stringify(body),
    });

    // 403 en una mutación suele ser un token CSRF caducado: se pide uno nuevo y se reintenta una vez.
    if (response.status === 403 && MUTATING.has(method) && !retried) {
      return this.request<T>(method, path, body, true);
    }
    if (!response.ok) {
      throw await toApiError(response);
    }
    if (response.status === 204 || response.headers.get('Content-Length') === '0') {
      return undefined as T;
    }
    return (await response.json()) as T;
  }

  private async csrfToken(forceRefresh: boolean): Promise<string> {
    const existing = readCookie(CSRF_COOKIE, this.cookies());
    if (existing && !forceRefresh) {
      return existing;
    }
    const response = await this.fetchFn('/api/v1/auth/csrf', { credentials: 'same-origin' });
    if (!response.ok) {
      throw await toApiError(response);
    }
    const token = readCookie(CSRF_COOKIE, this.cookies());
    if (!token) {
      throw new ApiError(0, 'No se pudo obtener el token de seguridad. Comprueba que las cookies estén habilitadas.');
    }
    return token;
  }
}

const GENERIC_MESSAGES: Record<number, string> = {
  401: 'Tu sesión no es válida. Inicia sesión de nuevo.',
  403: 'No tienes permiso para realizar esta acción.',
  404: 'No se encontró el recurso.',
  413: 'Los datos enviados son demasiado grandes.',
  429: 'Demasiadas solicitudes. Espera un momento.',
};

async function toApiError(response: Response): Promise<ApiError> {
  let problem: { detail?: string; errors?: Record<string, string>; issues?: ValidationIssue[] } = {};
  try {
    problem = await response.json();
  } catch {
    // Respuesta sin cuerpo JSON: se usa el mensaje genérico.
  }
  const message = problem.detail ?? GENERIC_MESSAGES[response.status] ?? `Error inesperado (${response.status}).`;
  return new ApiError(response.status, message, problem.errors ?? {}, problem.issues ?? []);
}

export const api = new ApiClient();
