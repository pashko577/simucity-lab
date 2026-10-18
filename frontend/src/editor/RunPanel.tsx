import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';

import { ApiError } from '../api/client';
import { batchesApi, runsApi } from '../api/endpoints';
import type { Batch, BatchStatus, Run } from '../api/types';
import { formatMeanSd } from '../comparison/format';
import { ErrorMessage } from '../ui/ErrorMessage';
import { formatMetricValue, METRIC_INFO, RUN_STATUS_LABEL } from '../ui/labels';
import { NumberField } from '../ui/NumberField';
import { useEditor } from './store';
import { useSaveScenario } from './useSaveScenario';

const POLL_MS = 700;

const BATCH_STATUS_LABEL: Record<BatchStatus, string> = {
  PENDING: 'En cola',
  RUNNING: 'Calculando',
  COMPLETED: 'Completado',
  PARTIAL: 'Completado parcialmente',
  FAILED: 'Fallido',
  CANCELLED: 'Cancelado',
};

const ACTIVE = new Set(['PENDING', 'RUNNING']);

export function RunPanel() {
  const scenarioId = useEditor((s) => s.scenarioId);
  const projectId = useEditor((s) => s.projectId);
  const { setIssues, setPlayback } = useEditor.getState();
  const save = useSaveScenario();
  const [durationMinutes, setDurationMinutes] = useState(15);
  const [repetitions, setRepetitions] = useState(1);
  const [seedText, setSeedText] = useState('');
  const [run, setRun] = useState<Run | null>(null);
  const [batch, setBatch] = useState<Batch | null>(null);
  const [error, setError] = useState<unknown>(null);
  const polling = useRef<number | null>(null);

  useEffect(() => () => {
    if (polling.current !== null) {
      window.clearTimeout(polling.current);
    }
  }, []);

  /** Al terminar, se anima la ejecución indicada (la individual, o la primera completada del lote). */
  const showPlayback = async (runId: string) => {
    const payload = await runsApi.samples(runId);
    setPlayback({ runId, payload, timeSeconds: 0, playing: true, speed: 20 });
  };

  const pollRun = (runId: string) => {
    polling.current = window.setTimeout(async () => {
      try {
        const current = await runsApi.get(runId);
        setRun(current);
        if (ACTIVE.has(current.status)) {
          pollRun(runId);
        } else if (current.status === 'COMPLETED') {
          setIssues(current.warnings ?? []);
          await showPlayback(runId);
        }
      } catch (e) {
        setError(e);
      }
    }, POLL_MS);
  };

  const pollBatch = (batchId: string) => {
    polling.current = window.setTimeout(async () => {
      try {
        const current = await batchesApi.get(batchId);
        setBatch(current);
        if (ACTIVE.has(current.status)) {
          pollBatch(batchId);
        } else {
          const first = current.runs.find((r) => r.status === 'COMPLETED');
          if (first) {
            await showPlayback(first.id);
          }
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
    setRun(null);
    setBatch(null);
    try {
      await save();
      const seed = seedText.trim() === '' ? undefined : Number(seedText);
      const params = { durationSeconds: durationMinutes * 60, seed };
      if (repetitions > 1) {
        const created = await batchesApi.create(scenarioId, repetitions, params);
        setBatch(created);
        pollBatch(created.id);
      } else {
        const created = await runsApi.create(scenarioId, params);
        setRun(created);
        pollRun(created.id);
      }
    } catch (e) {
      if (e instanceof ApiError && e.issues.length > 0) {
        setIssues(e.issues); // Se muestran en el panel de validación y se resaltan en el lienzo.
      }
      setError(e);
    }
  };

  const cancel = async () => {
    try {
      if (batch) {
        setBatch(await batchesApi.cancel(batch.id));
      } else if (run) {
        setRun(await runsApi.cancel(run.id));
      }
    } catch (e) {
      setError(e);
    }
  };

  const active = (run && ACTIVE.has(run.status)) || (batch && ACTIVE.has(batch.status));

  return (
    <section className="panel">
      <h2>Ejecutar simulación</h2>
      <div className="field-row align-end">
        <NumberField label="Duración" unit="min" min={1} max={120} value={durationMinutes}
          onCommit={setDurationMinutes} />
        <NumberField label="Repeticiones" unit="semillas" integer min={1} max={10} value={repetitions}
          onCommit={setRepetitions} />
      </div>
      <div className="field">
        <label htmlFor="seed">
          {repetitions > 1 ? 'Semilla inicial' : 'Semilla'} <span className="unit">(opcional)</span>
        </label>
        <input id="seed" inputMode="numeric" pattern="-?[0-9]*" placeholder="aleatoria" value={seedText}
          onChange={(e) => setSeedText(e.target.value)} />
      </div>
      {repetitions > 1 && (
        <p className="muted small">
          Se ejecutará {repetitions} veces con semillas consecutivas. Para comparar dos escenarios, usa la misma
          semilla inicial en ambos.
        </p>
      )}
      <div className="button-row">
        <button type="button" className="primary" onClick={start} disabled={!!active}>Ejecutar</button>
        {active && <button type="button" onClick={cancel}>Cancelar</button>}
      </div>
      <ErrorMessage error={error} />

      {run && <SingleRunResult run={run} projectId={projectId} />}
      {batch && <BatchResult batch={batch} projectId={projectId} />}
    </section>
  );
}

function SingleRunResult({ run, projectId }: { run: Run; projectId: string | null }) {
  return (
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
          {projectId && <Link to={`/projects/${projectId}/compare?base=${run.id}`}>Comparar con otra ejecución</Link>}
        </p>
      )}
      <Disclaimer />
    </div>
  );
}

function BatchResult({ batch, projectId }: { batch: Batch; projectId: string | null }) {
  const lastSeed = batch.baseSeed + batch.size - 1;
  const finished = !ACTIVE.has(batch.status);
  return (
    <div className="run-result">
      <p>
        <strong>{BATCH_STATUS_LABEL[batch.status]}</strong>
        <span className="muted small"> · {batch.completedRuns}/{batch.size} ejecuciones · semillas {batch.baseSeed}–{lastSeed}</span>
      </p>
      {!finished && (
        <progress max={batch.size} value={batch.completedRuns} aria-label="Progreso del lote" />
      )}
      {finished && batch.summary.length > 0 && (
        <table className="metrics">
          <caption className="muted small">Media ± desviación típica entre semillas</caption>
          <tbody>
            {batch.summary.map((m) => (
              <tr key={m.name} title={METRIC_INFO[m.name]?.help}>
                <th scope="row">{METRIC_INFO[m.name]?.label ?? m.name}</th>
                <td>{formatMeanSd(m.mean, m.standardDeviation, m.unit)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {finished && batch.completedRuns > 0 && (
        <p className="muted small">La animación muestra la primera semilla del lote.</p>
      )}
      {finished && batch.completedRuns >= 2 && projectId && (
        <p className="button-row">
          <Link to={`/projects/${projectId}/compare?mode=batches&base=${batch.id}`}>Comparar con otro lote</Link>
        </p>
      )}
      <Disclaimer />
    </div>
  );
}

function Disclaimer() {
  return (
    <p className="muted small">
      Resultados exploratorios de un modelo simplificado: no predicen el tráfico real sin calibración.
    </p>
  );
}
