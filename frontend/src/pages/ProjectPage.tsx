import { type FormEvent, useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { projectsApi, scenariosApi } from '../api/endpoints';
import type { Project, ScenarioSummary } from '../api/types';
import { AppHeader } from '../ui/AppHeader';
import { ErrorMessage } from '../ui/ErrorMessage';

export function ProjectPage() {
  const { projectId = '' } = useParams();
  const navigate = useNavigate();
  const [project, setProject] = useState<Project | null>(null);
  const [scenarios, setScenarios] = useState<ScenarioSummary[] | null>(null);
  const [name, setName] = useState('');
  const [error, setError] = useState<unknown>(null);

  const reload = useCallback(() => {
    projectsApi.get(projectId).then(setProject).catch(setError);
    scenariosApi.list(projectId).then((page) => setScenarios(page.content)).catch(setError);
  }, [projectId]);

  useEffect(reload, [reload]);

  const create = async (event: FormEvent) => {
    event.preventDefault();
    try {
      const scenario = await scenariosApi.create(projectId, name.trim());
      navigate(`/scenarios/${scenario.id}`);
    } catch (e) {
      setError(e);
    }
  };

  const duplicate = async (scenario: ScenarioSummary) => {
    await scenariosApi.duplicate(scenario.id).catch(setError);
    reload();
  };

  const remove = async (scenario: ScenarioSummary) => {
    if (!window.confirm(`¿Eliminar "${scenario.name}" y sus ejecuciones?`)) {
      return;
    }
    await scenariosApi.remove(scenario.id).catch(setError);
    reload();
  };

  return (
    <>
      <AppHeader />
      <main className="page">
        <p><Link to="/projects">← Proyectos</Link></p>
        <h1>{project?.name ?? '…'}</h1>
        {project?.description && <p className="muted">{project.description}</p>}

        <div className="panel-title">
          <h2>Escenarios</h2>
          <Link className="button-link" to={`/projects/${projectId}/compare`}>Comparar ejecuciones</Link>
        </div>
        <form className="inline-form" onSubmit={create}>
          <input aria-label="Nombre del escenario" placeholder="Nombre del nuevo escenario" required maxLength={120}
            value={name} onChange={(e) => setName(e.target.value)} />
          <button type="submit" className="primary">Crear y abrir editor</button>
        </form>
        <ErrorMessage error={error} />

        {scenarios === null ? <p className="muted">Cargando…</p> : scenarios.length === 0 ? (
          <p className="muted">Este proyecto no tiene escenarios.</p>
        ) : (
          <ul className="list">
            {scenarios.map((s) => (
              <li key={s.id} className="list-item">
                <Link to={`/scenarios/${s.id}`}>{s.name}</Link>
                <span className="muted">Actualizado {new Date(s.updatedAt).toLocaleString('es')}</span>
                <button type="button" className="subtle" onClick={() => duplicate(s)}>Duplicar</button>
                <button type="button" className="danger subtle" onClick={() => remove(s)}>Eliminar</button>
              </li>
            ))}
          </ul>
        )}
      </main>
    </>
  );
}
