import type { DemandDef, EdgeDef, MovementDef, NodeDef, NodeType, ScenarioDefinition, SignalDef } from '../api/types';
import { splitPhasing } from './signalTemplates';

/**
 * Operaciones puras sobre una ScenarioDefinition. Todas devuelven una definición nueva (inmutabilidad):
 * el store de Zustand solo las combina.
 */

export const EMPTY_DEFINITION: ScenarioDefinition = {
  nodes: [],
  edges: [],
  demands: [],
  signals: [],
  prohibitedMovements: [],
};

/** Valores por defecto de una calle nueva (parámetros pedagógicos, sección 8.4). */
export const EDGE_DEFAULTS = {
  lanes: 1,
  speedLimitMps: 50 / 3.6,
  /** Vehículos por metro de carril (≈ 7 m por vehículo). */
  storagePerMeterPerLane: 1 / 7,
  minLengthMeters: 10,
};

// --- Coordenadas -------------------------------------------------------------------------------------
// El motor usa el eje Y hacia ARRIBA; la pantalla (React Flow) hacia ABAJO. Toda conversión pasa por aquí.

export interface FlowPosition {
  x: number;
  y: number;
}

export function toFlowPosition(node: Pick<NodeDef, 'x' | 'y'>): FlowPosition {
  return { x: node.x, y: -node.y };
}

export function fromFlowPosition(position: FlowPosition): { x: number; y: number } {
  return { x: Math.round(position.x), y: Math.round(-position.y) };
}

// --- IDs ---------------------------------------------------------------------------------------------

/** Siguiente ID libre con el prefijo dado: N1, N2... Nunca reutiliza uno existente. */
export function nextId(prefix: string, existing: Iterable<string>): string {
  let max = 0;
  const pattern = new RegExp(`^${prefix}(\\d+)$`);
  for (const id of existing) {
    const match = pattern.exec(id);
    if (match) {
      max = Math.max(max, Number(match[1]));
    }
  }
  return `${prefix}${max + 1}`;
}

const NODE_PREFIX: Record<NodeType, string> = {
  INTERSECTION: 'I',
  ENTRY: 'E',
  EXIT: 'S',
  CONNECTOR: 'K',
};

// --- Nodos -------------------------------------------------------------------------------------------

export function addNode(def: ScenarioDefinition, type: NodeType, x: number, y: number): [ScenarioDefinition, string] {
  const id = nextId(NODE_PREFIX[type], def.nodes.map((n) => n.id));
  return [{ ...def, nodes: [...def.nodes, { id, type, x: Math.round(x), y: Math.round(y) }] }, id];
}

export function moveNode(def: ScenarioDefinition, nodeId: string, x: number, y: number): ScenarioDefinition {
  return { ...def, nodes: def.nodes.map((n) => (n.id === nodeId ? { ...n, x, y } : n)) };
}

/** Cambiar el tipo invalida demandas que ya no tienen sentido y el semáforo si deja de ser intersección. */
export function changeNodeType(def: ScenarioDefinition, nodeId: string, type: NodeType): ScenarioDefinition {
  const nodes = def.nodes.map((n) => (n.id === nodeId ? { ...n, type } : n));
  const demands = def.demands.filter(
    (d) => (d.origin !== nodeId || type === 'ENTRY') && (d.destination !== nodeId || type === 'EXIT'),
  );
  const signals = type === 'INTERSECTION' ? def.signals : def.signals.filter((s) => s.nodeId !== nodeId);
  return { ...def, nodes, demands, signals };
}

/** Borrado en cascada: calles, demandas, semáforo y giros prohibidos que dependen del nodo. */
export function removeNode(def: ScenarioDefinition, nodeId: string): ScenarioDefinition {
  const removedEdges = new Set(def.edges.filter((e) => e.from === nodeId || e.to === nodeId).map((e) => e.id));
  const withoutNode: ScenarioDefinition = {
    ...def,
    nodes: def.nodes.filter((n) => n.id !== nodeId),
    edges: def.edges.filter((e) => !removedEdges.has(e.id)),
    demands: def.demands.filter((d) => d.origin !== nodeId && d.destination !== nodeId),
    signals: def.signals.filter((s) => s.nodeId !== nodeId),
    prohibitedMovements: def.prohibitedMovements.filter((m) => !removedEdges.has(m.from) && !removedEdges.has(m.to)),
  };
  return regenerateSignalsAround(withoutNode, [...removedEdges].map((id) => def.edges.find((e) => e.id === id)!));
}

// --- Calles ------------------------------------------------------------------------------------------

export function distance(a: Pick<NodeDef, 'x' | 'y'>, b: Pick<NodeDef, 'x' | 'y'>): number {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

/** Calle nueva con longitud igual a la distancia en el lienzo (1 unidad = 1 m) y capacidad proporcional. */
export function addEdge(def: ScenarioDefinition, from: string, to: string): [ScenarioDefinition, string | null] {
  const a = def.nodes.find((n) => n.id === from);
  const b = def.nodes.find((n) => n.id === to);
  if (!a || !b || from === to || def.edges.some((e) => e.from === from && e.to === to)) {
    return [def, null];
  }
  const length = Math.max(EDGE_DEFAULTS.minLengthMeters, Math.round(distance(a, b)));
  const edge: EdgeDef = {
    id: nextId('C', def.edges.map((e) => e.id)),
    from,
    to,
    lengthMeters: length,
    lanes: EDGE_DEFAULTS.lanes,
    speedLimitMps: EDGE_DEFAULTS.speedLimitMps,
    storageCapacity: Math.max(1, Math.round(length * EDGE_DEFAULTS.storagePerMeterPerLane)),
    closed: false,
  };
  const next = { ...def, edges: [...def.edges, edge] };
  return [regenerateSignalsAround(next, [edge]), edge.id];
}

/** Crea el sentido contrario de una calle, copiando sus propiedades. */
export function addReverseEdge(def: ScenarioDefinition, edgeId: string): [ScenarioDefinition, string | null] {
  const edge = def.edges.find((e) => e.id === edgeId);
  if (!edge) {
    return [def, null];
  }
  const [next, reverseId] = addEdge(def, edge.to, edge.from);
  if (!reverseId) {
    return [def, null];
  }
  const { id: _ignored, from: _f, to: _t, ...props } = edge;
  return [updateEdge(next, reverseId, props), reverseId];
}

export function updateEdge(def: ScenarioDefinition, edgeId: string, changes: Partial<Omit<EdgeDef, 'id' | 'from' | 'to'>>): ScenarioDefinition {
  return { ...def, edges: def.edges.map((e) => (e.id === edgeId ? { ...e, ...changes } : e)) };
}

/**
 * Cambia los carriles y escala la capacidad en la misma proporción: más carriles también son más espacio.
 * Sin esto, ampliar una calle apenas cambiaría el resultado (el almacenamiento seguiría limitando el flujo)
 * y el experimento sería engañoso. La capacidad sigue siendo editable a mano después.
 */
export function setLanes(def: ScenarioDefinition, edgeId: string, lanes: number): ScenarioDefinition {
  const edge = def.edges.find((e) => e.id === edgeId);
  if (!edge || lanes === edge.lanes) {
    return def;
  }
  const storageCapacity = Math.max(1, Math.round((edge.storageCapacity * lanes) / edge.lanes));
  return updateEdge(def, edgeId, { lanes, storageCapacity });
}

export function removeEdge(def: ScenarioDefinition, edgeId: string): ScenarioDefinition {
  const edge = def.edges.find((e) => e.id === edgeId);
  if (!edge) {
    return def;
  }
  const next: ScenarioDefinition = {
    ...def,
    edges: def.edges.filter((e) => e.id !== edgeId),
    prohibitedMovements: def.prohibitedMovements.filter((m) => m.from !== edgeId && m.to !== edgeId),
  };
  return regenerateSignalsAround(next, [edge]);
}

// --- Giros prohibidos --------------------------------------------------------------------------------

export function isProhibited(def: ScenarioDefinition, movement: MovementDef): boolean {
  return def.prohibitedMovements.some((m) => m.from === movement.from && m.to === movement.to);
}

export function toggleProhibited(def: ScenarioDefinition, movement: MovementDef, nodeId: string): ScenarioDefinition {
  const prohibitedMovements = isProhibited(def, movement)
    ? def.prohibitedMovements.filter((m) => m.from !== movement.from || m.to !== movement.to)
    : [...def.prohibitedMovements, movement];
  return regenerateSignal({ ...def, prohibitedMovements }, nodeId);
}

/** Giros posibles en un nodo (sin giro en U), para mostrarlos en el panel. */
export function movementsAt(def: ScenarioDefinition, nodeId: string): MovementDef[] {
  const incoming = def.edges.filter((e) => e.to === nodeId);
  const outgoing = def.edges.filter((e) => e.from === nodeId);
  return incoming.flatMap((in_) =>
    outgoing.filter((out) => out.to !== in_.from).map((out) => ({ from: in_.id, to: out.id })),
  );
}

// --- Semáforos ---------------------------------------------------------------------------------------

export interface SignalSettings {
  greenSeconds: number;
  allRedSeconds: number;
}

export const DEFAULT_SIGNAL: SignalSettings = { greenSeconds: 30, allRedSeconds: 3 };

/** Lee verde y todo-rojo de un plan generado por la plantilla (fase 0 = verde, fase 1 = todo-rojo). */
export function signalSettings(signal: SignalDef): SignalSettings {
  const green = signal.phases.find((p) => p.greenMovements.length > 0)?.durationSeconds ?? DEFAULT_SIGNAL.greenSeconds;
  const allRed = signal.phases.find((p) => p.greenMovements.length === 0)?.durationSeconds ?? 0;
  return { greenSeconds: green, allRedSeconds: allRed };
}

export function setSignal(def: ScenarioDefinition, nodeId: string, settings: SignalSettings | null): ScenarioDefinition {
  const others = def.signals.filter((s) => s.nodeId !== nodeId);
  if (!settings) {
    return { ...def, signals: others };
  }
  // Un plan sin fases (intersección aún sin calles) se conserva: el motor lo reporta como SIGNAL_NO_PHASES y
  // se completa solo al conectar calles (regenerateSignal). Descartarlo haría desaparecer el semáforo sin aviso.
  const plan = splitPhasing(def, nodeId, settings.greenSeconds, settings.allRedSeconds);
  return { ...def, signals: [...others, plan] };
}

/** Rehace el plan de una intersección con semáforo tras cambiar sus calles o giros, conservando los tiempos. */
export function regenerateSignal(def: ScenarioDefinition, nodeId: string): ScenarioDefinition {
  const current = def.signals.find((s) => s.nodeId === nodeId);
  return current ? setSignal(def, nodeId, signalSettings(current)) : def;
}

function regenerateSignalsAround(def: ScenarioDefinition, edges: EdgeDef[]): ScenarioDefinition {
  const nodes = new Set(edges.flatMap((e) => [e.from, e.to]));
  return [...nodes].reduce(regenerateSignal, def);
}

// --- Demandas ----------------------------------------------------------------------------------------

export function addDemand(def: ScenarioDefinition, origin: string, destination: string): [ScenarioDefinition, string] {
  const demand: DemandDef = {
    id: nextId('D', def.demands.map((d) => d.id)),
    origin,
    destination,
    vehiclesPerMinute: 10,
    mode: 'POISSON',
    profile: [],
  };
  return [{ ...def, demands: [...def.demands, demand] }, demand.id];
}

export function updateDemand(def: ScenarioDefinition, demandId: string, changes: Partial<Omit<DemandDef, 'id'>>): ScenarioDefinition {
  return { ...def, demands: def.demands.map((d) => (d.id === demandId ? { ...d, ...changes } : d)) };
}

export function removeDemand(def: ScenarioDefinition, demandId: string): ScenarioDefinition {
  return { ...def, demands: def.demands.filter((d) => d.id !== demandId) };
}

// --- Unidades ----------------------------------------------------------------------------------------

export const kmhToMps = (kmh: number): number => kmh / 3.6;
export const mpsToKmh = (mps: number): number => Math.round(mps * 3.6 * 10) / 10;
