import type { CompactSample, SamplesPayload } from '../api/types';

/**
 * Lógica pura de reproducción. La animación solo muestra estados ya calculados por el motor (sección 2.3):
 * aquí no se simula nada, solo se elige qué muestra mostrar y cómo dibujarla.
 */

export interface EdgeFrame {
  vehicles: number;
  queued: number;
}

export interface PlaybackFrame {
  timeSeconds: number;
  sample: CompactSample;
  edges: Map<string, EdgeFrame>;
}

/** Índice de la última muestra con t <= time (búsqueda binaria). Antes de la primera, la primera. */
export function sampleIndexAt(samples: readonly CompactSample[], timeSeconds: number): number {
  let low = 0;
  let high = samples.length - 1;
  let result = 0;
  while (low <= high) {
    const mid = (low + high) >> 1;
    if (samples[mid]!.t <= timeSeconds) {
      result = mid;
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }
  return result;
}

export function frameAt(payload: SamplesPayload, timeSeconds: number): PlaybackFrame | null {
  if (payload.samples.length === 0) {
    return null;
  }
  const sample = payload.samples[sampleIndexAt(payload.samples, timeSeconds)]!;
  const edges = new Map<string, EdgeFrame>();
  payload.edgeIds.forEach((id, i) => {
    edges.set(id, { vehicles: sample.vehicles[i] ?? 0, queued: sample.queuedByEdge[i] ?? 0 });
  });
  return { timeSeconds, sample, edges };
}

export function lastTime(payload: SamplesPayload): number {
  return payload.samples.at(-1)?.t ?? 0;
}

/**
 * Color por ocupación (vehículos / capacidad): verde (libre) → amarillo → rojo (llena).
 * Se usa HSL para una transición continua; la leyenda de la interfaz explica la escala.
 */
export function occupancyColor(ratio: number): string {
  const clamped = Math.min(1, Math.max(0, ratio));
  const hue = 130 - 130 * clamped;
  return `hsl(${Math.round(hue)}, 75%, 45%)`;
}

/**
 * Posición de cada vehículo como fracción de la calle (0 = inicio, 1 = final).
 * Los vehículos en cola se apilan desde el final (donde está el semáforo o el cruce); el resto se reparte
 * en el tramo libre. Es una representación visual del recuento: el modelo no tiene posiciones exactas.
 */
export function vehicleFractions(vehicles: number, queued: number, capacity: number): number[] {
  const total = Math.max(0, vehicles);
  const inQueue = Math.min(Math.max(0, queued), total);
  const spacing = 1 / Math.max(capacity, total, 1);
  const fractions: number[] = [];
  for (let i = 0; i < inQueue; i++) {
    fractions.push(1 - spacing * (i + 0.5));
  }
  const moving = total - inQueue;
  const freeEnd = 1 - spacing * inQueue;
  for (let i = 0; i < moving; i++) {
    fractions.push((freeEnd * (i + 0.5)) / moving);
  }
  return fractions;
}

/** Formato mm:ss para la línea de tiempo. */
export function formatClock(seconds: number): string {
  const total = Math.max(0, Math.floor(seconds));
  const minutes = Math.floor(total / 60);
  const rest = total % 60;
  return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`;
}
