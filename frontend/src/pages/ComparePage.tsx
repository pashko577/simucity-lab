import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';

import { comparisonsApi, runsApi } from '../api/endpoints';
import type { ComparisonResult, ProjectRunItem } from '../api/types';
import {
  assessmentView,
  describeChange,
  formatDifference,
  formatPercent,
  KEY_METRICS,
  runOptionLabel,
  topSegments,
} from '../comparison/format';
import { MetricBars } from '../comparison/MetricBars';
import { AppHeader } from '../ui/AppHeader';
import { ErrorMessage } from '../ui/ErrorMessage';
import { formatMetricValue, METRIC_INFO } from '../ui/labels';

/**
 * Comparador de escenarios (secciones 5.1 y 9): A = ejecución base, B = alternativa. La selección vive en la
 * URL (?base=…&candidate=…) para poder compartir o recargar la comparación.
 */
export function ComparePage() {
  const { projectId = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const baseId = params.get('base') ?? '';
  const candidateId = params.get('candidate') ?? '';
  const [runs, setRuns] = useState<ProjectRunItem[] | null>(null);
  const [result, setResult] = useState<ComparisonResult | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    runsApi.listInProject(projectId).then((page) => setRuns(page.content)).catch(setError);
  }, [projectId]);

  useEffect(() => {
    setResult(null);
    setError(null);
    if (baseId && candidateId && baseId !== candidateId) {
      comparisonsApi.compare(baseId, candidateId).then(setResult).catch(setError);
    }
  }, [baseId, candidateId]);

  const choose = (key: 'base' | 'candidate', value: string) => {
    const next = new URLSearchParams(params);
    if (value) {
      next.set(key, value);
    } else {
      next.delete(key);
    }
    setParams(next, { replace: true });
  };

  const swap = () => setParams({ base: candidateId, candidate: baseId }, { replace: true });

  return (
    <>
      <AppHeader />
      <main className="page page--wide">
        <p><Link to={`/projects/${projectId}`}>← Proyecto</Link></p>
        <h1>Comparar ejecuciones</h1>
        <p className="muted">
          Para una comparación justa, ejecuta ambos escenarios con la <strong>misma semilla</strong>, demanda y duración,
          y cambia solo lo que quieres estudiar (por ejemplo: duplica el escenario y amplía una calle).
        </p>

        {runs && runs.length < 2 && (
          <p className="warning">Necesitas al menos dos ejecuciones terminadas en este proyecto para comparar.</p>
        )}

        <div className="compare-pickers">
          <RunPicker label="A · Base" id="base" runs={runs} value={baseId} exclude={candidateId}
            onChange={(v) => choose('base', v)} />
          <button type="button" className="subtle" onClick={swap} disabled={!baseId || !candidateId}
            aria-label="Intercambiar A y B">⇄</button>
          <RunPicker label="B · Alternativa" id="candidate" runs={runs} value={candidateId} exclude={baseId}
            onChange={(v) => choose('candidate', v)} />
        </div>

        <ErrorMessage error={error} />
        {baseId && candidateId && !result && !error && <p className="muted">Comparando…</p>}
        {result && <ComparisonView result={result} />}
      </main>
    </>
  );
}

function RunPicker({ label, id, runs, value, exclude, onChange }: {
  label: string; id: string; runs: ProjectRunItem[] | null; value: string; exclude: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="field">
      <label htmlFor={`run-${id}`}>{label}</label>
      <select id={`run-${id}`} value={value} onChange={(e) => onChange(e.target.value)} disabled={!runs}>
        <option value="">{runs ? 'Elige una ejecución…' : 'Cargando…'}</option>
        {runs?.map((r) => (
          <option key={r.id} value={r.id} disabled={r.id === exclude}>{runOptionLabel(r)}</option>
        ))}
      </select>
    </div>
  );
}

function ComparisonView({ result }: { result: ComparisonResult }) {
  const differing = result.conditions.filter((c) => !c.equal);
  const keyMetrics = KEY_METRICS
    .map((name) => result.network.find((m) => m.name === name))
    .filter((m) => m !== undefined);
  const segments = topSegments(result.segments);

  return (
    <>
      <section className={`card fairness ${result.fair ? 'fairness--ok' : 'fairness--warn'}`} aria-live="polite">
        {result.fair ? (
          <p><strong>✓ Comparación justa:</strong> misma semilla, demanda, duración, paso temporal y versión del motor.
            Las diferencias se deben a los cambios del escenario.</p>
        ) : (
          <>
            <p><strong>⚠ Comparación con condiciones distintas:</strong> parte de la diferencia puede no deberse a los
              cambios del escenario (sección 9.2).</p>
            <ul>
              {differing.map((c) => (
                <li key={c.code}>
                  {c.label}{c.baseValue || c.candidateValue ? `: A = ${c.baseValue}, B = ${c.candidateValue}` : ' distinta'}
                </li>
              ))}
            </ul>
          </>
        )}
      </section>

      <section className="compare-section">
        <h2>Qué cambió de A a B</h2>
        {result.changes.length === 0 ? (
          <p className="muted">Los dos escenarios son iguales (las posiciones de los nodos no cuentan).</p>
        ) : (
          <ul className="change-list">
            {result.changes.map((c) => (
              <li key={`${c.elementType}-${c.elementId}`}>{describeChange(c)}</li>
            ))}
          </ul>
        )}
      </section>

      <section className="compare-section">
        <h2>Métricas clave</h2>
        <div className="legend-row" aria-label="Leyenda">
          <span><span className="swatch series-1" /> A · {result.base.scenarioName} (semilla {result.base.seed})</span>
          <span><span className="swatch series-2" /> B · {result.candidate.scenarioName} (semilla {result.candidate.seed})</span>
        </div>
        <div className="metric-grid">
          {keyMetrics.map((m) => <MetricBars key={m.name} metric={m} />)}
        </div>
      </section>

      <section className="compare-section">
        <h2>Todas las métricas de la red</h2>
        <div className="table-scroll">
        <table className="data-table">
          <thead>
            <tr><th scope="col">Métrica</th><th scope="col">A</th><th scope="col">B</th>
              <th scope="col">Diferencia</th><th scope="col">Variación</th><th scope="col">Evaluación</th></tr>
          </thead>
          <tbody>
            {result.network.map((m) => {
              const status = assessmentView(m.assessment);
              return (
                <tr key={m.name} title={METRIC_INFO[m.name]?.help}>
                  <th scope="row">{METRIC_INFO[m.name]?.label ?? m.name}</th>
                  <td>{formatMetricValue(m.base, m.unit)}</td>
                  <td>{formatMetricValue(m.candidate, m.unit)}</td>
                  <td>{formatDifference(m.difference, m.unit)}</td>
                  <td>{formatPercent(m.percentChange)}</td>
                  <td>
                    <span className={`status status--${status.tone}`}>
                      <span className="status__icon" aria-hidden="true">{status.icon}</span> {status.label}
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        </div>
      </section>

      {segments.length > 0 && (
        <section className="compare-section">
          <h2>Calles donde más cambió la cola</h2>
          <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr><th scope="col">Calle</th><th scope="col">Cola media A → B</th><th scope="col">Cola máxima A → B</th>
                <th scope="col">Utilización A → B</th></tr>
            </thead>
            <tbody>
              {segments.map((row) => (
                <tr key={row.edgeId}>
                  <th scope="row">{row.edgeId}</th>
                  <td>{pair(row.queueMean)}</td>
                  <td>{pair(row.queueMax)}</td>
                  <td>{pair(row.utilization)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        </section>
      )}

      <p className="muted small">
        Resultados exploratorios de un modelo simplificado. Con una sola semilla, una diferencia pequeña puede ser
        azar: repite con otras semillas antes de sacar conclusiones (sección 9.2).
      </p>
    </>
  );
}

function pair(m: { base: number | null; candidate: number | null; unit: string } | undefined): string {
  return m ? `${formatMetricValue(m.base, m.unit)} → ${formatMetricValue(m.candidate, m.unit)}` : '—';
}
