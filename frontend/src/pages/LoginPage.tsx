import { type FormEvent, useState } from 'react';
import { useNavigate } from 'react-router';

import { authApi } from '../api/endpoints';
import { ErrorMessage } from '../ui/ErrorMessage';
import { useSession } from '../ui/session';

export function LoginPage() {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const login = useSession((s) => s.login);
  const navigate = useNavigate();

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === 'register') {
        await authApi.register(email, password);
      }
      await login(email, password);
      navigate('/projects');
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="auth-page">
      <form className="card auth-card" onSubmit={submit}>
        <h1>SimuCity Lab</h1>
        <p className="muted">Laboratorio de experimentación urbana</p>

        <div className="tabs" role="tablist">
          <button type="button" role="tab" aria-selected={mode === 'login'} onClick={() => setMode('login')}>
            Iniciar sesión
          </button>
          <button type="button" role="tab" aria-selected={mode === 'register'} onClick={() => setMode('register')}>
            Crear cuenta
          </button>
        </div>

        <div className="field">
          <label htmlFor="email">Email</label>
          <input id="email" type="email" autoComplete="email" required value={email}
            onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="password">Contraseña</label>
          <input id="password" type="password" required minLength={mode === 'register' ? 12 : undefined}
            autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
            value={password} onChange={(e) => setPassword(e.target.value)} />
          {mode === 'register' && <small className="muted">Mínimo 12 caracteres.</small>}
        </div>

        <ErrorMessage error={error} />
        <button type="submit" className="primary" disabled={busy}>
          {mode === 'login' ? 'Entrar' : 'Crear cuenta y entrar'}
        </button>
      </form>
    </main>
  );
}
