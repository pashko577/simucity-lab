import { useState } from 'react';

import { scenariosApi } from '../api/endpoints';
import { ErrorMessage } from '../ui/ErrorMessage';
import { useEditor } from './store';
import { useSaveScenario } from './useSaveScenario';

export function ValidationPanel() {
  const issues = useEditor((s) => s.issues);
  const scenarioId = useEditor((s) => s.scenarioId);
  const definition = useEditor((s) => s.definition);
  const { setIssues, select } = useEditor.getState();
  const save = useSaveScenario();
  const [busy, setBusy] = useState(false);
  const [checked, setChecked] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const validate = async () => {
    if (!scenarioId) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await save();
      const result = await scenariosApi.validate(scenarioId);
      setIssues(result.issues);
      setChecked(true);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  const focus = (elementId: string | null) => {
    if (!elementId) {
      return;
    }
    if (definition.nodes.some((n) => n.id === elementId)) {
      select({ kind: 'node', id: elementId });
    } else if (definition.edges.some((e) => e.id === elementId)) {
      select({ kind: 'edge', id: elementId });
    }
  };

  const errors = issues.filter((i) => i.severity === 'ERROR').length;

  return (
    <section className="panel">
      <div className="panel-title">
        <h2>Validación</h2>
        <button type="button" onClick={validate} disabled={busy}>{busy ? 'Validando…' : 'Validar'}</button>
      </div>
      <ErrorMessage error={error} />
      {checked && issues.length === 0 && <p className="ok">✓ El escenario es válido.</p>}
      {issues.length > 0 && (
        <>
          <p className={errors > 0 ? 'error' : 'warning'}>
            {errors > 0 ? `${errors} error(es): hay que corregirlos para ejecutar.` : 'Solo advertencias: se puede ejecutar.'}
          </p>
          <ul className="issue-list">
            {issues.map((issue, i) => (
              <li key={`${issue.code}-${i}`} className={`issue issue--${issue.severity.toLowerCase()}`}>
                <button type="button" className="link" onClick={() => focus(issue.elementId)}
                  disabled={!issue.elementId}>
                  {issue.message}
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}
