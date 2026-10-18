import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';

import { batchesApi, comparisonsApi, runsApi } from '../api/endpoints';
import type {
  BatchComparisonResult,
  BatchListItem,
  ComparisonResult,
  Condition,
  ProjectRunItem,
  ScenarioChange,
} from '../api/types';
import {
  assessmentView,
  batchOptionLabel,
  describeChange,
  formatDifference,
  formatInterval,
  formatMeanSd,
  formatPercent,
  KEY_METRICS,
  runOptionLabel,
  topSegments,
} from '../comparison/format';
import { type BarsData, MetricBars } from '../comparison/MetricBars';
import { AppHeader } from '../ui/AppHeader';
import { ErrorMessage } from '../ui/ErrorMessage';
import { formatMetricValue, METRIC_INFO } from '../ui/labels';

type Mode = 'runs' | 'batches';

/**
 * Comparador de escenarios (secciones 5.1 y 9): A = base, B = alternativa. Dos modos: ejecuciones individuales
 * o lotes de varias semillas (con intervalos de confianza). La selección vive en la URL.
 */
export function ComparePage() {
  const { projectId = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const mode: Mode = params.get('mode') === 'batches' ? 'batches' : 'runs';
  const baseId = params.get('base') ?? '';
  const candidateId = params.get('candidate') ?? '';

  const [runs, setRuns] = useState<ProjectRunItem[] | null>(null);
  const [batches, setBatches] = useState<BatchListItem[] | null>(null);
  const [runResult, setRunResult] = useState<ComparisonResult | null>(null);
  const [batchResult, setBatchResult] = useState<BatchComparisonResult | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    runsApi.listInProject(projectId).then((page) => setRuns(page.content)).catch(setError);
    batchesApi.listInProject(projectId).then((page) => setBatches(page.content)).catch(setError);
  }, [projectId]);

  useEffect(() => {
    setRunResult(null);
    setBatchResult(null);
    setError(null);
    if (!baseId || !candidateId || baseId === candidateId) {
      return;
    }
    if (mode === 'runs') {
      comparisonsApi.compare(baseId, candidateId).then(setRunResult).catch(setError);
    } else {
      comparisonsApi.compareBatches(baseId, candidateId).then(setBatchResult).catch(setError);
    }
  }, [mode, baseId, candidateId]);

  const choose = (key: 'base' | 'candidate', value: string) => {
    const next = new URLSearchParams(params);
    if (value) {
      next.set(key, value);
    } else {
      next.delete(key);
    }
    setParams(next, { replace: true });
  };

  const setMode = (next: Mode) => setParams(next === 'batches' ? { mode: next } : {}, { replace: true });

  const swap = () => {
    const next = new URLSearchParams(params);
    next.set('base', candidateId);
    next.set('candidate', baseId);
    setParams(next, { replace: true });
  };

  const usableBatches = batches?.filter((b) => b.status === 'COMPLETED' || b.status === 'PARTIAL') ?? null;
  const options = mode === 'runs'
    ? runs?.map((r) => ({ id: r.id, label: runOptionLabel(r) })) ?? null
    : usableBatches?.map((b) => ({ id: b.id, label: batchOptionLabel(b) })) ?? null;

  return (
    <>
      <AppHeader />
      <main className="page page--wide">
        <p><Link to={`/projects/${projectId}`}>← Proyecto</Link></p>
        <h1>Comparar escenarios</h1>

        <div className="tabs tabs--inline" role="tablist" aria-label="Qué comparar">
          <button type="button" role="tab" aria-selected={mode === 'runs'} onClick={() => setMode('runs')}>
            Ejecuciones individuales
          </button>
          <button type="button" role="tab" aria-selected={mode === 'batches'} onClick={() => setMode('batches')}>
            Lotes (varias semillas)
          </button>
        </div>

        <p className="muted">
          {mode === 'runs'
            ? <>Compara dos ejecuciones con la <strong>misma semilla</strong>, demanda y duración, cambiando solo lo
              que quieres estudiar. Con una sola semilla, una diferencia pequeña puede ser azar.</>
            : <>Compara dos lotes ejecutados con las <strong>mismas semillas</strong>: cada semilla se compara con
              su pareja y se calcula un intervalo de confianza del 95 %. Si el intervalo incluye el cero, la diferencia
              puede ser azar y se marca como <strong>no concluyente</strong>.</>}
        </p>

        {options && options.length < 2 && (
          <p className="warning">
            {mode === 'runs'
              ? 'Necesitas al menos dos ejecuciones terminadas en este proyecto.'
              : 'Necesitas al menos dos lotes terminados. En el editor, ejecuta con "Repeticiones" mayor que 1.'}
          </p>
        )}

        <div className="compare-pickers">
          <Picker label="A · Base" id="base" options={options} value={baseId} exclude={candidateId}
            onChange={(v) => choose('base', v)} />
          <button type="button" className="subtle" onClick={swap} disabled={!baseId || !candidateId}
            aria-label="Intercambiar A y B">⇄</button>
          <Picker label="B · Alternativa" id="candidate" options={options} value={candidateId} exclude={baseId}
            onChange={(v) => choose('candidate', v)} />
        </div>

        <ErrorMessage error={error} />
        {baseId && candidateId && !runResult && !batchResult && !error && <p className="muted">Comparando…</p>}
        {runResult && <RunComparisonView result={runResult} />}
        {batchResult && <BatchComparisonView result={batchResult} />}
      </main>
    </>
  );
}

function Picker({ label, id, options, value, exclude, onChange }: {
  label: string; id: string; options: { id: string; label: string }[] | null; value: string; exclude: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="field">
      <label htmlFor={`pick-${id}`}>{label}</label>
      <select id={`pick-${id}`} value={value} onChange={(e) => onChange(e.target.value)} disabled={!options}>
        <option value="">{options ? 'Elige…' : 'Cargando…'}</option>
        {options?.map((o) => <option key={o.id} value={o.id} disabled={o.id === exclude}>{o.label}</option>)}
      </select>
    </div>
  );
}

// --- Secciones compartidas -----------------------------------------------------------------------------

function FairnessBanner({ fair, conditions, extra }: { fair: boolean; conditions: Condition[]; extra?: string }) {
  const differing = conditions.filter((c) => !c.equal);
  return (
    <section className={`card fairness ${fair ? 'fairness--ok' : 'fairness--warn'}`} aria-live="polite">
      {fair ? (
        <p><strong>✓ Comparación justa:</strong> mismas semillas, demanda, duración, paso temporal y versión del motor.
          Las diferencias se deben a los cambios del escenario.{extra && ` ${extra}`}</p>
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
          {extra && <p>{extra}</p>}
        </>
      )}
    </section>
  );
}

function ChangesSection({ changes }: { changes: ScenarioChange[] }) {
  return (
    <section className="compare-section">
      <h2>Qué cambió de A a B</h2>
      {changes.length === 0 ? (
        <p className="muted">Los dos escenarios son iguales (las posiciones de los nodos no cuentan).</p>
      ) : (
        <ul className="change-list">
          {changes.map((c) => <li key={`${c.elementType}-${c.elementId}`}>{describeChange(c)}</li>)}
        </ul>
      )}
    </section>
  );
}

function KeyMetrics({ bars, legendA, legendB, note }: { bars: BarsData[]; legendA: string; legendB: string; note?: string }) {
  return (
    <section className="compare-section">
      <h2>Métricas clave</h2>
      <div className="legend-row" aria-label="Leyenda">
        <span><span className="swatch series-1" /> A · {legendA}</span>
        <span><span className="swatch series-2" /> B · {legendB}</span>
        {note && <span className="muted">{note}</span>}
      </div>
      <div className="metric-grid">
        {bars.map((b) => <MetricBars key={b.name} data={b} />)}
      </div>
    </section>
  );
}

function Status({ assessment }: { assessment: BarsData['assessment'] }) {
  const status = assessmentView(assessment);
  return (
    <span className={`status status--${status.tone}`}>
      <span className="status__icon" aria-hidden="true">{status.icon}</span> {status.label}
    </span>
  );
}

function pickKey<T extends { name: string }>(metrics: T[]): T[] {
  return KEY_METRICS.map((name) => metrics.find((m) => m.name === name)).filter((m): m is T => m !== undefined);
}

// --- Ejecuciones individuales ----------------------------------------------------------------------------

function RunComparisonView({ result }: { result: ComparisonResult }) {
  const segments = topSegments(result.segments);
  return (
    <>
      <FairnessBanner fair={result.fair} conditions={result.conditions} />
      <ChangesSection changes={result.changes} />
      <KeyMetrics
        bars={pickKey(result.network).map((m) => ({ ...m }))}
        legendA={`${result.base.scenarioName} (semilla ${result.base.seed})`}
        legendB={`${result.candidate.scenarioName} (semilla ${result.candidate.seed})`} />

      <section className="compare-section">
        <h2>Todas las métricas de la red</h2>
        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr><th scope="col">Métrica</th><th scope="col">A</th><th scope="col">B</th>
                <th scope="col">Diferencia</th><th scope="col">Variación</th><th scope="col">Evaluación</th></tr>
            </thead>
            <tbody>
              {result.network.map((m) => (
                <tr key={m.name} title={METRIC_INFO[m.name]?.help}>
                  <th scope="row">{METRIC_INFO[m.name]?.label ?? m.name}</th>
                  <td>{formatMetricValue(m.base, m.unit)}</td>
                  <td>{formatMetricValue(m.candidate, m.unit)}</td>
                  <td>{formatDifference(m.difference, m.unit)}</td>
                  <td>{formatPercent(m.percentChange)}</td>
                  <td><Status assessment={m.assessment} /></td>
                </tr>
              ))}
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
                <tr><th scope="col">Calle</th><th scope="col">Cola media A → B</th>
                  <th scope="col">Cola máxima A → B</th><th scope="col">Utilización A → B</th></tr>
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
        azar: para confirmarla, compara lotes de varias semillas.
      </p>
    </>
  );
}

function pair(m: { base: number | null; candidate: number | null; unit: string } | undefined): string {
  return m ? `${formatMetricValue(m.base, m.unit)} → ${formatMetricValue(m.candidate, m.unit)}` : '—';
}

// --- Lotes ----------------------------------------------------------------------------------------------

function BatchComparisonView({ result }: { result: BatchComparisonResult }) {
  const { base, candidate } = result;
  const method = result.paired
    ? `Comparación pareada: ${base.completedRuns} semillas, cada una con su pareja.`
    : 'Las semillas no coinciden: se comparan medias (Welch), con menos sensibilidad que la comparación pareada.';
  const incomplete = base.completedRuns < base.size || candidate.completedRuns < candidate.size;

  return (
    <>
      <FairnessBanner fair={result.fair} conditions={result.conditions} extra={method} />
      {incomplete && (
        <p className="warning">
          Algún lote tiene ejecuciones que no se completaron: A {base.completedRuns}/{base.size},
          B {candidate.completedRuns}/{candidate.size}. Solo se usan las completadas.
        </p>
      )}
      <ChangesSection changes={result.changes} />
      <KeyMetrics
        bars={pickKey(result.network).map((m) => ({
          name: m.name, unit: m.unit, base: m.baseMean, candidate: m.candidateMean,
          baseSpread: m.baseStandardDeviation, candidateSpread: m.candidateStandardDeviation,
          percentChange: m.percentChange, assessment: m.assessment,
        }))}
        legendA={`${base.scenarioName} (${base.completedRuns} semillas)`}
        legendB={`${candidate.scenarioName} (${candidate.completedRuns} semillas)`}
        note="Barra: media · línea: ± desviación típica" />

      <section className="compare-section">
        <h2>Todas las métricas de la red</h2>
        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr><th scope="col">Métrica</th><th scope="col">A (media ± desv.)</th>
                <th scope="col">B (media ± desv.)</th><th scope="col">Diferencia media</th>
                <th scope="col">IC 95 % de la diferencia</th><th scope="col">Variación</th>
                <th scope="col">Evaluación</th></tr>
            </thead>
            <tbody>
              {result.network.map((m) => (
                <tr key={m.name} title={METRIC_INFO[m.name]?.help}>
                  <th scope="row">{METRIC_INFO[m.name]?.label ?? m.name}</th>
                  <td>{formatMeanSd(m.baseMean, m.baseStandardDeviation, m.unit)}</td>
                  <td>{formatMeanSd(m.candidateMean, m.candidateStandardDeviation, m.unit)}</td>
                  <td>{formatDifference(m.meanDifference, m.unit)}</td>
                  <td>{formatInterval(m.ciLow, m.ciHigh, m.unit)}</td>
                  <td>{formatPercent(m.percentChange)}</td>
                  <td><Status assessment={m.assessment} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <p className="muted small">
        IC 95 %: con un 95 % de confianza, la diferencia real entre los escenarios está en ese rango. "No concluyente"
        significa que el rango incluye el cero: con estas semillas no se puede afirmar que haya diferencia. Más
        semillas estrechan el intervalo. Resultados exploratorios de un modelo simplificado.
      </p>
    </>
  );
}
