import type { NodeType, RunStatus } from '../api/types';

export const NODE_TYPE_LABEL: Record<NodeType, string> = {
  ENTRY: 'Entrada',
  EXIT: 'Salida',
  INTERSECTION: 'Intersección',
  CONNECTOR: 'Conector',
};

export const RUN_STATUS_LABEL: Record<RunStatus, string> = {
  PENDING: 'En cola',
  RUNNING: 'Calculando',
  COMPLETED: 'Completada',
  FAILED: 'Fallida',
  CANCELLED: 'Cancelada',
};

/** Nombre y explicación sencilla de cada métrica (sección 5.3: explicar cada indicador). */
export const METRIC_INFO: Record<string, { label: string; help: string }> = {
  trips_generated: { label: 'Vehículos generados', help: 'Vehículos que llegaron a alguna entrada.' },
  trips_completed: { label: 'Viajes completados', help: 'Vehículos que llegaron a su destino.' },
  trips_incomplete: { label: 'Viajes sin terminar', help: 'Seguían en la red o esperando entrar al final.' },
  travel_time_mean: { label: 'Tiempo medio de viaje', help: 'Desde que aparece el vehículo hasta que sale, incluida la espera para entrar.' },
  travel_time_p95: { label: 'Tiempo de viaje (percentil 95)', help: 'El 95 % de los viajes tardó esto o menos. Requiere al menos 20 viajes.' },
  wait_time_mean: { label: 'Espera media', help: 'Tiempo en que el vehículo podía avanzar pero estaba detenido (cola, semáforo, calle llena).' },
  throughput: { label: 'Flujo completado', help: 'Viajes completados por hora.' },
  queue_mean: { label: 'Cola media', help: 'Promedio en el tiempo de vehículos en cola.' },
  queue_max: { label: 'Cola máxima', help: 'Mayor número de vehículos en cola.' },
  vehicles_discharged: { label: 'Vehículos que pasaron', help: 'Vehículos que salieron de la calle.' },
  utilization: { label: 'Utilización', help: 'Vehículos que pasaron / capacidad de paso en la duración.' },
};

export function formatMetricValue(value: number | null, unit: string): string {
  if (value === null) {
    return 'No aplica';
  }
  const rounded = Math.abs(value) >= 100 ? Math.round(value) : Math.round(value * 10) / 10;
  if (unit === 'ratio') {
    return `${Math.round(value * 100)} %`;
  }
  return `${rounded.toLocaleString('es')} ${unit}`;
}
