import { ApiError } from '../api/client';

/** Mensaje de error legible. Nunca muestra trazas: el backend ya envía textos pensados para el usuario. */
export function errorText(error: unknown): string {
  if (error instanceof ApiError) {
    const fields = Object.entries(error.fieldErrors).map(([field, message]) => `${field}: ${message}`);
    return [error.message, ...fields].join(' · ');
  }
  return 'Ocurrió un error inesperado. Inténtalo de nuevo.';
}

export function ErrorMessage({ error }: { error: unknown }) {
  if (!error) {
    return null;
  }
  return <p role="alert" className="error">{errorText(error)}</p>;
}
