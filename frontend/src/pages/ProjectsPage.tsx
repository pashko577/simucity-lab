import { type FormEvent, useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router';

import { projectsApi } from '../api/endpoints';
import type { Project } from '../api/types';
import { AppHeader } from '../ui/AppHeader';
import { ErrorMessage } from '../ui/ErrorMessage';

export function ProjectsPage() {
  const [projects, setProjects] = useState<Project[] | null>(null);
  const [name, setName] = useState('');
  const [error, setError] = useState<unknown>(null);

  const reload = useCallback(() => {
    projectsApi.list().then((page) => setProjects(page.content)).catch(setError);
  }, []);

  useEffect(reload, [reload]);

  const create = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    try {
      await projectsApi.create(name.trim());
      setName('');
      reload();
    } catch (e) {
      setError(e);
    }
  };

  const remove = async (project: Project) => {
    if (!window.confirm(`¿Eliminar "${project.name}" con todos sus escenarios y ejecuciones?`)) {
      return;
    }
    await projectsApi.remove(project.id).catch(setError);
    reload();
  };

  return (
    <>
      <AppHeader />
      <main className="page">
        <h1>Mis proyectos</h1>
        <form className="inline-form" onSubmit={create}>
          <input aria-label="Nombre del proyecto" placeholder="Nombre del nuevo proyecto" required maxLength={120}
            value={name} onChange={(e) => setName(e.target.value)} />
          <button type="submit" className="primary">Crear proyecto</button>
        </form>
        <ErrorMessage error={error} />

        {projects === null ? <p className="muted">Cargando…</p> : projects.length === 0 ? (
          <p className="muted">Aún no tienes proyectos. Crea uno para empezar.</p>
        ) : (
          <ul className="list">
            {projects.map((p) => (
              <li key={p.id} className="list-item">
                <Link to={`/projects/${p.id}`}>{p.name}</Link>
                <span className="muted">Actualizado {new Date(p.updatedAt).toLocaleString('es')}</span>
                <button type="button" className="danger subtle" onClick={() => remove(p)}>Eliminar</button>
              </li>
            ))}
          </ul>
        )}
      </main>
    </>
  );
}
