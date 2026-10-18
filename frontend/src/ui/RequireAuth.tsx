import { useEffect, useState } from 'react';
import { Navigate, Outlet } from 'react-router';

import { useSession } from './session';

/** Protege las rutas privadas: sin sesión, redirige al login. */
export function RequireAuth() {
  const status = useSession((s) => s.status);
  const refresh = useSession((s) => s.refresh);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (status === 'unknown') {
      refresh().catch(() => setError('No se pudo conectar con el servidor.'));
    }
  }, [status, refresh]);

  if (error) {
    return <p className="page-message error">{error}</p>;
  }
  if (status === 'unknown') {
    return <p className="page-message">Cargando…</p>;
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}
