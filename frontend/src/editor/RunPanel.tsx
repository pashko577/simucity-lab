import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';

import { ApiError } from '../api/client';
import { runsApi } from '../api/endpoints';
import type { Run } from '../api/types';
import { ErrorMessage } from '../ui/ErrorMessage';
import { formatMetricValue, METRIC_INFO, RUN_STATUS_LABEL } from '../ui/labels';
import { NumberField } from '../ui/NumberField';
import { useEditor } from './store';
import { useSaveScenario } from './useSaveScenario';

const POLL_MS = 700;

export function RunPanel() {
  const scenarioId = useEditor((s) => s.scenarioId);
  const projectId = useEditor((s) => s.projectId);
  const { setIssues, setPlayback } = useEditor.getState();
  const save = useSaveScenario();
  const [durationMinutes, setDurationMinutes] = useState(15);
  const [seedText, setSeedText] = useState('');
  const [run, setRun] = useState<Run | null>(null);
  const [error, setError] = useState<unknown>(null);
  const polling = useRef<number | null>(null);

  useEffect(() => () => {
    if (polling.current !== null) {
      window.clearTimeout(polling.current);
    }
  }, []);

  const poll = (runId: string) => {
    polling.current = window.setTimeout(async () => {
      try {
        const current = await runsApi.get(runId);
        setRun(current);
        if (current.status === 'PENDING' || current.status === 'RUNNING') {
          poll(runId);
        } else if (current.status === 'COMPLETED') {
          const payload = await runsApi.samples(runId);
          setPlayback({ runId, payload, timeSeconds: 0, playing: true, speed: 20 });
          setIssues(current.warnings ?? []);
        }
      } catch (e) {
        setError(e);
      }
    }, POLL_MS);
  };

  const start = async () => {
    if (!scenarioId) {
      return;
    }
    setError(null);
    setPlayback(null);
    try {
      await save();
      const seed = seedText.trim() === '' ? undefined : Number(seedText);
      const created = await runsApi.create(scenarioId, { durationSeconds: durationMinutes * 60, seed });
      setRun(created);
      poll(created.id);
    } catch (e) {
      if (e instanceof ApiError && e.issues.length > 0) {
        setIssues(e.issues); // Se muestran en el panel de validación y se resaltan en el lienzo.
      }
      setError(e);
    }
  };

  const cancel = async () => {
    if (run) {
      setRun(await runsApi.cancel(run.id).catch((e) => { setError(e); return run; }));
    }
  };

  const active = run?.status === 'PENDING' || run?.status === 'RUNNING';

  return (
    <section className="panel">
      <h2>Ejecutar simulación</h2>
      <div className="field-row align-end">
        <NumberField label="Duración" unit="min" min={1} max={120} value={durationMinutes}
          onCommit={setDurationMinutes} />
        <div className="field">
          <label htmlFor="seed">Semilla <span className="unit">(opcional)</span></label>
          <input id="seed" inputMode="numeric" pattern="-?[0-9]*" placeholder="aleatoria" value={seedText}
            onChange={(e) => setSeedText(e.target.value)} />
        </div>
      </div>
      <div className="button-row">
        <button type="button" className="primary" onClick={start} disabled={active}>Ejecutar</button>
        {active && <button type="button" onClick={cancel}>Cancelar</button>}
      </div>
      <ErrorMessage error={error} />

      {run && (
        <div className="run-result">
          <p>
            <strong>{RUN_STATUS_LABEL[run.status]}</strong>
            <span className="muted small"> · semilla {run.seed} · motor {run.engineVersion}</span>
          </p>
          {run.errorDetail && <p className="error">{run.errorDetail}</p>}
          {run.networkMetrics && (
            <table className="metrics">
              <tbody>
                {run.networkMetrics.map((m) => (
                  <tr key={m.name} title={METRIC_INFO[m.name]?.help}>
                    <th scope="row">{METRIC_INFO[m.name]?.label ?? m.name}</th>
                    <td>{formatMetricValue(m.value, m.unit)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {run.anomalies && run.anomalies.length > 0 && (
            <ul className="issue-list">
              {run.anomalies.map((a) => <li key={a.code + a.elementId} className="issue issue--warning">{a.message}</li>)}
            </ul>
          )}
          {run.status === 'COMPLETED' && (
            <p className="button-row">
              <a href={runsApi.csvUrl(run.id)} download>Descargar métricas (CSV)</a>
              {projectId && (
                <Link to={`/projects/${projectId}/compare?base=${run.id}`}>Comparar con otra ejecución</Link>
              )}
            </p>
          )}
          <p className="muted small">
            Resultados exploratorios de un modelo simplificado: no predicen el tráfico real sin calibración.
          </p>
        </div>
      )}
    </section>
  );
}
