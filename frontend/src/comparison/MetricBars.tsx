import type { MetricComparison } from '../api/types';
import { formatMetricValue, METRIC_INFO } from '../ui/labels';
import { assessmentView, formatPercent } from './format';

const BAR_HEIGHT = 18; // ≤ 24 px: la barra no llena la banda
const BAR_GAP = 8;
const LABEL_WIDTH = 22; // "A" / "B" a la izquierda: identidad sin depender del color
const VALUE_SPACE = 92; // espacio para el valor en la punta de la barra
const WIDTH = 320;
const RADIUS = 4;

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
 * B en el de la serie 2; el valor va en la punta en color de texto, y "A"/"B" rotulan cada barra.
 */
export function MetricBars({ metric }: { metric: MetricComparison }) {
  const info = METRIC_INFO[metric.name];
  const status = assessmentView(metric.assessment);
  const max = Math.max(metric.base ?? 0, metric.candidate ?? 0, 1e-9);
  const plotWidth = WIDTH - LABEL_WIDTH - VALUE_SPACE;
  const rows = [
    { key: 'A', value: metric.base, className: 'series-1' },
    { key: 'B', value: metric.candidate, className: 'series-2' },
  ];
  const height = rows.length * BAR_HEIGHT + (rows.length - 1) * BAR_GAP;

  return (
    <figure className="metric-bars">
      <figcaption>
        <span className="metric-bars__title">{info?.label ?? metric.name}</span>
        <span className="metric-bars__unit"> ({metric.unit})</span>
        <span className={`status status--${status.tone}`}>
          <span className="status__icon" aria-hidden="true">{status.icon}</span>
          {' '}{status.label} {metric.percentChange !== null && formatPercent(metric.percentChange)}
        </span>
      </figcaption>
      <svg viewBox={`0 0 ${WIDTH} ${height}`} width="100%" role="img"
        aria-label={`${info?.label ?? metric.name}: A ${formatMetricValue(metric.base, metric.unit)}, B ${formatMetricValue(metric.candidate, metric.unit)}`}>
        {rows.map((row, i) => {
          const y = i * (BAR_HEIGHT + BAR_GAP);
          const width = row.value === null ? 0 : (row.value / max) * plotWidth;
          return (
            <g key={row.key}>
              <text x={0} y={y + BAR_HEIGHT / 2} dominantBaseline="central" className="bar-label">{row.key}</text>
              <path d={barPath(LABEL_WIDTH, y, width, BAR_HEIGHT)} className={`bar ${row.className}`}>
                <title>{`${row.key}: ${formatMetricValue(row.value, metric.unit)}`}</title>
              </path>
              <text x={LABEL_WIDTH + width + 6} y={y + BAR_HEIGHT / 2} dominantBaseline="central" className="bar-value">
                {formatMetricValue(row.value, metric.unit)}
              </text>
            </g>
          );
        })}
      </svg>
    </figure>
  );
}
