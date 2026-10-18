import type { Assessment } from '../api/types';
import { formatMetricValue, METRIC_INFO } from '../ui/labels';
import { assessmentView, formatPercent } from './format';

const BAR_HEIGHT = 18; // ≤ 24 px: la barra no llena la banda
const BAR_GAP = 8;
const LABEL_WIDTH = 22; // "A" / "B" a la izquierda: identidad sin depender del color
const VALUE_SPACE = 92; // espacio para el valor a la derecha de la barra
const WIDTH = 320;
const RADIUS = 4;
const CAP = 4; // media altura de los topes de la línea de dispersión

/** Datos de un pequeño gráfico: una ejecución por lado, o la media de un lote con su dispersión. */
export interface BarsData {
  name: string;
  unit: string;
  base: number | null;
  candidate: number | null;
  /** Desviación típica entre semillas (solo lotes). */
  baseSpread?: number | null;
  candidateSpread?: number | null;
  percentChange: number | null;
  assessment: Assessment;
}

/** Barra horizontal con el extremo de datos redondeado (4 px) y cuadrada en la línea base. */
function barPath(x: number, y: number, width: number, height: number): string {
  if (width <= 0) {
    return '';
  }
  const r = Math.min(RADIUS, width, height / 2);
  return `M ${x} ${y} H ${x + width - r} Q ${x + width} ${y} ${x + width} ${y + r} `
    + `V ${y + height - r} Q ${x + width} ${y + height} ${x + width - r} ${y + height} H ${x} Z`;
}

/**
 * Una pequeña gráfica por métrica (no hay dos ejes: cada métrica tiene su escala). A en el color de la serie 1,
 * B en el de la serie 2; el valor va junto a la barra en color de texto, y "A"/"B" rotulan cada barra.
 * En lotes, una línea fina con topes marca ± la desviación típica entre semillas.
 */
export function MetricBars({ data }: { data: BarsData }) {
  const info = METRIC_INFO[data.name];
  const status = assessmentView(data.assessment);
  const rows = [
    { key: 'A', value: data.base, spread: data.baseSpread ?? null, className: 'series-1' },
    { key: 'B', value: data.candidate, spread: data.candidateSpread ?? null, className: 'series-2' },
  ];
  const max = Math.max(...rows.map((r) => (r.value ?? 0) + (r.spread ?? 0)), 1e-9);
  const plotWidth = WIDTH - LABEL_WIDTH - VALUE_SPACE;
  const scale = (v: number) => (Math.max(0, v) / max) * plotWidth;
  const height = rows.length * BAR_HEIGHT + (rows.length - 1) * BAR_GAP;
  const describe = (v: number | null, spread: number | null) =>
    formatMetricValue(v, data.unit) + (spread !== null && v !== null ? ` ± ${formatMetricValue(spread, data.unit)}` : '');

  return (
    <figure className="metric-bars">
      <figcaption>
        <span className="metric-bars__title">{info?.label ?? data.name}</span>
        <span className="metric-bars__unit"> ({data.unit})</span>
        <span className={`status status--${status.tone}`}>
          <span className="status__icon" aria-hidden="true">{status.icon}</span>
          {' '}{status.label} {data.percentChange !== null && formatPercent(data.percentChange)}
        </span>
      </figcaption>
      <svg viewBox={`0 0 ${WIDTH} ${height}`} width="100%" role="img"
        aria-label={`${info?.label ?? data.name}: A ${describe(data.base, data.baseSpread ?? null)}, `
          + `B ${describe(data.candidate, data.candidateSpread ?? null)}`}>
        {rows.map((row, i) => {
          const y = i * (BAR_HEIGHT + BAR_GAP);
          const cy = y + BAR_HEIGHT / 2;
          const width = row.value === null ? 0 : scale(row.value);
          const hasSpread = row.value !== null && row.spread !== null && row.spread > 0;
          const lo = hasSpread ? LABEL_WIDTH + scale(row.value! - row.spread!) : 0;
          const hi = hasSpread ? LABEL_WIDTH + scale(row.value! + row.spread!) : 0;
          const labelX = Math.max(LABEL_WIDTH + width, hasSpread ? hi : 0) + 6;
          return (
            <g key={row.key}>
              <text x={0} y={cy} dominantBaseline="central" className="bar-label">{row.key}</text>
              <path d={barPath(LABEL_WIDTH, y, width, BAR_HEIGHT)} className={`bar ${row.className}`}>
                <title>{`${row.key}: ${describe(row.value, row.spread)}`}</title>
              </path>
              {hasSpread && (
                <g className="spread" aria-hidden="true">
                  <line x1={lo} x2={hi} y1={cy} y2={cy} />
                  <line x1={lo} x2={lo} y1={cy - CAP} y2={cy + CAP} />
                  <line x1={hi} x2={hi} y1={cy - CAP} y2={cy + CAP} />
                </g>
              )}
              <text x={labelX} y={cy} dominantBaseline="central" className="bar-value">
                {formatMetricValue(row.value, data.unit)}
              </text>
            </g>
          );
        })}
      </svg>
    </figure>
  );
}
