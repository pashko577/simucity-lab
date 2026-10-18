import { describe, expect, it } from 'vitest';

import type { MetricComparison } from '../api/types';
import { assessmentView, describeChange, formatDifference, formatPercent, topSegments } from './format';

describe('formatPercent y formatDifference', () => {
  it('usa signo, coma decimal y n/a si la base es 0', () => {
    expect(formatPercent(-25)).toBe('−25 %');
    expect(formatPercent(12.46)).toBe('+12,5 %');
    expect(formatPercent(0)).toBe('0 %');
    expect(formatPercent(null)).toBe('n/a');
  });

  it('diferencia absoluta con unidad', () => {
    expect(formatDifference(-10.04, 's')).toBe('−10 s');
    expect(formatDifference(250.4, 'veh')).toBe('+250 veh');
    expect(formatDifference(null, 's')).toBe('—');
  });
});

describe('assessmentView', () => {
  it('mejora y empeoramiento usan roles de estado con icono y texto, nunca solo color', () => {
    expect(assessmentView('IMPROVED')).toEqual({ icon: '▲', label: 'Mejora', tone: 'good' });
    expect(assessmentView('WORSENED')).toEqual({ icon: '▼', label: 'Empeora', tone: 'critical' });
    expect(assessmentView('NOT_COMPARABLE').tone).toBe('neutral');
  });
});

describe('describeChange', () => {
  it('describe un cambio de calle en lenguaje sencillo y con unidades del editor', () => {
    expect(describeChange({
      elementType: 'EDGE', elementId: 'C1', kind: 'MODIFIED',
      fields: [
        { field: 'lanes', before: 1, after: 2 },
        { field: 'speedLimitMps', before: 13.8889, after: 16.6667 },
        { field: 'closed', before: false, after: true },
      ],
    })).toBe('Calle C1: carriles: 1 → 2; velocidad: 50 km/h → 60 km/h; cerrada: no → sí');
  });

  it('altas y bajas', () => {
    expect(describeChange({ elementType: 'SIGNAL', elementId: 'I1', kind: 'ADDED', fields: [] }))
      .toBe('Semáforo en I1: añadido');
    expect(describeChange({ elementType: 'DEMAND', elementId: 'D2', kind: 'REMOVED', fields: [] }))
      .toBe('Demanda D2: eliminado');
  });
});

describe('topSegments', () => {
  const m = (name: string, edge: string, difference: number): MetricComparison => ({
    name, scope: 'SEGMENT', scopeId: edge, unit: 'veh', base: 0, candidate: difference,
    difference, percentChange: null, assessment: 'NEUTRAL',
  });

  it('agrupa por calle y ordena por el cambio absoluto de la cola media', () => {
    const rows = topSegments([
      m('queue_mean', 'C1', -1), m('queue_max', 'C1', -3),
      m('queue_mean', 'C2', 8), m('utilization', 'C2', 0.1),
      m('queue_mean', 'C3', -5),
    ], 2);

    expect(rows.map((r) => r.edgeId)).toEqual(['C2', 'C3']);
    expect(rows[0]!.utilization?.difference).toBe(0.1);
  });
});
