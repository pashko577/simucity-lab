import type {
  Assessment,
  BatchListItem,
  FieldChange,
  MetricComparison,
  ProjectRunItem,
  ScenarioChange,
} from '../api/types';
import { mpsToKmh } from '../editor/model';

/** Lógica pura de presentación de una comparación (sin DOM): textos, evaluación y selección de filas. */

/** Métricas que se grafican: una pequeña gráfica por métrica, porque tienen unidades distintas. */
export const KEY_METRICS = ['travel_time_mean', 'wait_time_mean', 'queue_mean', 'trips_completed'] as const;

export interface AssessmentView {
  icon: string;
  label: string;
  /** Rol de estado reservado (good / critical) o neutro; nunca un color de serie. */
  tone: 'good' | 'critical' | 'neutral';
}

export function assessmentView(assessment: Assessment): AssessmentView {
  switch (assessment) {
    case 'IMPROVED':
      return { icon: '▲', label: 'Mejora', tone: 'good' };
    case 'WORSENED':
      return { icon: '▼', label: 'Empeora', tone: 'critical' };
    case 'UNCHANGED':
      return { icon: '=', label: 'Sin cambios', tone: 'neutral' };
    case 'NEUTRAL':
      return { icon: '·', label: 'Descriptiva', tone: 'neutral' };
    case 'NOT_COMPARABLE':
      return { icon: '–', label: 'No comparable', tone: 'neutral' };
    case 'INCONCLUSIVE':
      return { icon: '◆', label: 'No concluyente', tone: 'neutral' };
  }
}

/** Redondeo simétrico (por magnitud): −1,25 → −1,3, igual que 1,25 → 1,3. Math.round redondea hacia +∞. */
function round(value: number): number {
  const magnitude = Math.abs(value);
  const rounded = magnitude >= 100 ? Math.round(magnitude) : Math.round(magnitude * 10) / 10;
  return Math.sign(value) * rounded;
}

function signed(value: number): string {
  const r = round(value);
  return `${r < 0 ? '−' : ''}${Math.abs(r).toLocaleString('es')}`;
}

/** "40,2 ± 1,3 s": media y desviación típica entre semillas. */
export function formatMeanSd(mean: number | null, sd: number | null, unit: string): string {
  if (mean === null) {
    return 'No aplica';
  }
  const shown = unit === 'ratio' ? '' : ` ${unit}`;
  return `${round(mean).toLocaleString('es')} ± ${round(sd ?? 0).toLocaleString('es')}${shown}`;
}

/** Intervalo de confianza del 95 % de la diferencia: "[−12,9; −9] s". */
export function formatInterval(low: number | null, high: number | null, unit: string): string {
  if (low === null || high === null) {
    return '—';
  }
  return `[${signed(low)}; ${signed(high)}]${unit === 'ratio' ? '' : ` ${unit}`}`;
}

/** "+12,5 %", "−25 %" o "n/a" si la base es 0 (sección 9.3: no dividir por cero). */
export function formatPercent(percent: number | null): string {
  if (percent === null) {
    return 'n/a';
  }
  const rounded = Math.sign(percent) * (Math.round(Math.abs(percent) * 10) / 10);
  const sign = rounded > 0 ? '+' : rounded < 0 ? '−' : '';
  return `${sign}${Math.abs(rounded).toLocaleString('es')} %`;
}

export function formatDifference(difference: number | null, unit: string): string {
  if (difference === null) {
    return '—';
  }
  const rounded = round(difference);
  const sign = rounded > 0 ? '+' : rounded < 0 ? '−' : '';
  return `${sign}${Math.abs(rounded).toLocaleString('es')} ${unit === 'ratio' ? '' : unit}`.trim();
}

// --- Cambios en el escenario -----------------------------------------------------------------------

const ELEMENT_LABEL: Record<ScenarioChange['elementType'], string> = {
  NODE: 'Nodo',
  EDGE: 'Calle',
  DEMAND: 'Demanda',
  SIGNAL: 'Semáforo en',
  TURN: 'Giro prohibido',
};

const FIELD_LABEL: Record<string, string> = {
  type: 'tipo',
  from: 'origen',
  to: 'destino',
  lengthMeters: 'longitud',
  lanes: 'carriles',
  speedLimitMps: 'velocidad',
  storageCapacity: 'capacidad',
  closed: 'cerrada',
  origin: 'origen',
  destination: 'destino',
  vehiclesPerMinute: 'tasa',
  mode: 'llegadas',
  profile: 'perfil por periodos',
  offsetSeconds: 'desfase',
  phases: 'fases',
};

/** Valor legible con su unidad (la velocidad se muestra en km/h, como en el editor). */
export function formatFieldValue(field: string, value: unknown): string {
  if (value === null || value === undefined) {
    return '—';
  }
  switch (field) {
    case 'speedLimitMps':
      return `${mpsToKmh(Number(value))} km/h`;
    case 'lengthMeters':
      return `${value} m`;
    case 'storageCapacity':
      return `${value} veh`;
    case 'vehiclesPerMinute':
      return `${value} veh/min`;
    case 'offsetSeconds':
      return `${value} s`;
    case 'closed':
      return value ? 'sí' : 'no';
    case 'mode':
      return value === 'POISSON' ? 'aleatorias' : 'regulares';
    case 'phases':
      return Array.isArray(value) ? value.join(' | ') : String(value);
    case 'profile':
      return Array.isArray(value) ? `${value.length} periodo(s)` : String(value);
    default:
      return String(value);
  }
}

export function describeField(change: FieldChange): string {
  const label = FIELD_LABEL[change.field] ?? change.field;
  return `${label}: ${formatFieldValue(change.field, change.before)} → ${formatFieldValue(change.field, change.after)}`;
}

export function describeChange(change: ScenarioChange): string {
  const subject = `${ELEMENT_LABEL[change.elementType]} ${change.elementId}`;
  switch (change.kind) {
    case 'ADDED':
      return `${subject}: añadido`;
    case 'REMOVED':
      return `${subject}: eliminado`;
    case 'MODIFIED':
      return `${subject}: ${change.fields.map(describeField).join('; ')}`;
  }
}

// --- Métricas por calle ----------------------------------------------------------------------------

export interface SegmentRow {
  edgeId: string;
  queueMax: MetricComparison | undefined;
  queueMean: MetricComparison | undefined;
  utilization: MetricComparison | undefined;
}

/** Calles ordenadas por el cambio absoluto de su cola media: arriba, donde más cambió el tráfico. */
export function topSegments(segments: MetricComparison[], limit = 10): SegmentRow[] {
  const byEdge = new Map<string, SegmentRow>();
  for (const m of segments) {
    if (!m.scopeId) {
      continue;
    }
    const row = byEdge.get(m.scopeId) ?? { edgeId: m.scopeId, queueMax: undefined, queueMean: undefined, utilization: undefined };
    if (m.name === 'queue_max') row.queueMax = m;
    if (m.name === 'queue_mean') row.queueMean = m;
    if (m.name === 'utilization') row.utilization = m;
    byEdge.set(m.scopeId, row);
  }
  const impact = (row: SegmentRow) => Math.abs(row.queueMean?.difference ?? 0);
  return [...byEdge.values()]
    .sort((a, b) => impact(b) - impact(a) || a.edgeId.localeCompare(b.edgeId))
    .slice(0, limit);
}

// --- Selección de ejecuciones ----------------------------------------------------------------------

export function batchOptionLabel(batch: BatchListItem): string {
  const when = new Date(batch.createdAt).toLocaleString('es', { dateStyle: 'short', timeStyle: 'short' });
  const last = batch.baseSeed + batch.size - 1;
  return `${batch.scenarioName} · ${batch.size} semillas (${batch.baseSeed}–${last}) · `
    + `${Math.round(batch.durationSeconds / 60)} min · ${when}`;
}

export function runOptionLabel(run: ProjectRunItem): string {
  const when = new Date(run.finishedAt ?? run.createdAt).toLocaleString('es', { dateStyle: 'short', timeStyle: 'short' });
  return `${run.scenarioName} · semilla ${run.seed} · ${Math.round(run.durationSeconds / 60)} min · ${when}`;
}
