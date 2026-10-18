// Espejo de los DTO del backend (lab.simucity.app.*). Mantener sincronizado con docs/arquitectura-backend.md.

export interface User {
  id: string;
  email: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Project {
  id: string;
  name: string;
  description: string | null;
  visibility: 'PRIVATE';
  createdAt: string;
  updatedAt: string;
}

// --- Escenarios (ScenarioDefinition, esquema v1). Coordenadas con el eje Y hacia ARRIBA. ---

export type NodeType = 'INTERSECTION' | 'ENTRY' | 'EXIT' | 'CONNECTOR';
export type ArrivalMode = 'PERIODIC' | 'POISSON';

export interface NodeDef {
  id: string;
  type: NodeType;
  x: number;
  y: number;
}

export interface EdgeDef {
  id: string;
  from: string;
  to: string;
  lengthMeters: number;
  lanes: number;
  speedLimitMps: number;
  storageCapacity: number;
  closed: boolean;
}

export interface PeriodDef {
  fromSeconds: number;
  toSeconds: number;
  vehiclesPerMinute: number;
}

export interface DemandDef {
  id: string;
  origin: string;
  destination: string;
  vehiclesPerMinute: number;
  mode: ArrivalMode;
  profile?: PeriodDef[] | null;
}

export interface MovementDef {
  from: string;
  to: string;
}

export interface PhaseDef {
  durationSeconds: number;
  greenMovements: MovementDef[];
}

export interface SignalDef {
  nodeId: string;
  offsetSeconds: number;
  phases: PhaseDef[];
}

export interface ScenarioDefinition {
  nodes: NodeDef[];
  edges: EdgeDef[];
  demands: DemandDef[];
  signals: SignalDef[];
  prohibitedMovements: MovementDef[];
}

export interface ScenarioSummary {
  id: string;
  projectId: string;
  name: string;
  createdAt: string;
  updatedAt: string;
}

export interface Scenario extends ScenarioSummary {
  schemaVersion: number;
  definition: ScenarioDefinition;
}

export interface ValidationIssue {
  severity: 'ERROR' | 'WARNING';
  code: string;
  message: string;
  elementId: string | null;
}

export interface ValidationResponse {
  valid: boolean;
  issues: ValidationIssue[];
}

// --- Ejecuciones ---

export type RunStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
export type MetricScope = 'NETWORK' | 'SEGMENT' | 'ENTRY';

export interface RunParameters {
  seed: number;
  dtSeconds: number;
  durationSeconds: number;
  sampleIntervalSeconds: number;
  saturationFlowPerLaneVph: number;
  stallThresholdSeconds: number;
}

export interface RunParametersRequest {
  durationSeconds: number;
  seed?: number;
}

export interface Metric {
  name: string;
  value: number | null;
  unit: string;
  scope: MetricScope;
  scopeId: string | null;
  observations: number;
}

export interface Anomaly {
  code: string;
  message: string;
  elementId: string;
  atSeconds: number;
}

export interface Run {
  id: string;
  scenarioId: string;
  status: RunStatus;
  engineVersion: string;
  seed: number;
  parameters: RunParameters;
  scenarioSchemaVersion: number;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  errorCode: string | null;
  errorDetail: string | null;
  networkMetrics: Metric[] | null;
  anomalies: Anomaly[] | null;
  warnings: ValidationIssue[] | null;
}

export interface RunListItem {
  id: string;
  scenarioId: string;
  status: RunStatus;
  seed: number;
  engineVersion: string;
  createdAt: string;
  finishedAt: string | null;
}

/** Muestras compactas: los arreglos siguen el orden de edgeIds. */
export interface SamplesPayload {
  edgeIds: string[];
  samples: CompactSample[];
}

export interface CompactSample {
  tick: number;
  t: number;
  inNetwork: number;
  queued: number;
  waitingAtEntries: number;
  completed: number;
  vehicles: number[];
  queuedByEdge: number[];
}
