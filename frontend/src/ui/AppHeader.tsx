import type { ReactNode } from 'react';
import { Link, useNavigate } from 'react-router';

import { useSession } from './session';

export function AppHeader({ children }: { children?: ReactNode }) {
  const user = useSession((s) => s.user);
  const logout = useSession((s) => s.logout);
  const navigate = useNavigate();

  return (
    <header className="app-header">
      <Link to="/projects" className="brand">SimuCity Lab</Link>
      <div className="header-content">{children}</div>
      <span className="muted">{user?.email}</span>
      <button type="button" className="subtle" onClick={() => logout().then(() => navigate('/login'))}>
        Salir
      </button>
    </header>
  );
}
