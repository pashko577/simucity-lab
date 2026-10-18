import { describe, expect, it } from 'vitest';

import type { CompactSample, SamplesPayload } from '../api/types';
import { formatClock, frameAt, occupancyColor, sampleIndexAt, vehicleFractions } from './samples';

function sample(t: number, vehicles: number[], queued: number[]): CompactSample {
  return { tick: t * 2, t, inNetwork: 0, queued: 0, waitingAtEntries: 0, completed: 0, vehicles, queuedByEdge: queued };
}

const payload: SamplesPayload = {
  edgeIds: ['e1', 'e2'],
  samples: [sample(0, [0, 0], [0, 0]), sample(5, [3, 1], [1, 0]), sample(10, [5, 2], [4, 1])],
};

describe('sampleIndexAt', () => {
  it('elige la última muestra con t <= tiempo', () => {
    expect(sampleIndexAt(payload.samples, 0)).toBe(0);
    expect(sampleIndexAt(payload.samples, 4.9)).toBe(0);
    expect(sampleIndexAt(payload.samples, 5)).toBe(1);
    expect(sampleIndexAt(payload.samples, 99)).toBe(2);
    expect(sampleIndexAt(payload.samples, -1)).toBe(0);
  });
});

describe('frameAt', () => {
  it('asocia cada valor con su calle según edgeIds', () => {
    const frame = frameAt(payload, 7)!;
    expect(frame.edges.get('e1')).toEqual({ vehicles: 3, queued: 1 });
    expect(frame.edges.get('e2')).toEqual({ vehicles: 1, queued: 0 });
  });

  it('sin muestras devuelve null', () => {
    expect(frameAt({ edgeIds: [], samples: [] }, 0)).toBeNull();
  });
});

describe('vehicleFractions', () => {
  it('dibuja tantos vehículos como indica el recuento, todos dentro de la calle', () => {
    const fractions = vehicleFractions(5, 2, 10);
    expect(fractions).toHaveLength(5);
    fractions.forEach((f) => expect(f).toBeGreaterThan(0));
    fractions.forEach((f) => expect(f).toBeLessThan(1));
  });

  it('los vehículos en cola quedan al final de la calle', () => {
    const [firstQueued, secondQueued, ...moving] = vehicleFractions(4, 2, 10);
    expect(firstQueued).toBeGreaterThan(0.9);
    expect(secondQueued).toBeGreaterThan(0.8);
    moving.forEach((f) => expect(f).toBeLessThan(secondQueued!));
  });

  it('acota valores inconsistentes', () => {
    expect(vehicleFractions(2, 5, 10)).toHaveLength(2);
    expect(vehicleFractions(-1, 0, 10)).toHaveLength(0);
  });
});

describe('occupancyColor y formatClock', () => {
  it('verde vacío, rojo lleno', () => {
    expect(occupancyColor(0)).toBe('hsl(130, 75%, 45%)');
    expect(occupancyColor(1)).toBe('hsl(0, 75%, 45%)');
    expect(occupancyColor(5)).toBe('hsl(0, 75%, 45%)');
  });

  it('formatea minutos y segundos', () => {
    expect(formatClock(0)).toBe('00:00');
    expect(formatClock(125.7)).toBe('02:05');
  });
});
