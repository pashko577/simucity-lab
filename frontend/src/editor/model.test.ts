import { describe, expect, it } from 'vitest';

import type { ScenarioDefinition } from '../api/types';
import {
  addDemand,
  addEdge,
  addNode,
  addReverseEdge,
  changeNodeType,
  EMPTY_DEFINITION,
  fromFlowPosition,
  kmhToMps,
  movementsAt,
  mpsToKmh,
  nextId,
  removeEdge,
  removeNode,
  setLanes,
  setSignal,
  toFlowPosition,
  toggleProhibited,
} from './model';

/** Cruce en "+" de doble sentido: C en el centro; N, S, E, W a 100 m. */
function cross(): ScenarioDefinition {
  let def = EMPTY_DEFINITION;
  const place = (type: 'INTERSECTION' | 'CONNECTOR', x: number, y: number) => {
    const [next, id] = addNode(def, type, x, y);
    def = next;
    return id;
  };
  const c = place('INTERSECTION', 0, 0);
  for (const [x, y] of [[0, 100], [0, -100], [100, 0], [-100, 0]] as const) {
    const leg = place('CONNECTOR', x, y);
    const [withIn, inId] = addEdge(def, leg, c);
    const [withBoth] = addReverseEdge(withIn, inId!);
    def = withBoth;
  }
  return def;
}

describe('coordenadas', () => {
  it('invierte el eje Y entre el motor y la pantalla', () => {
    expect(toFlowPosition({ x: 10, y: 50 })).toEqual({ x: 10, y: -50 });
    expect(fromFlowPosition({ x: 10.4, y: -50.6 })).toEqual({ x: 10, y: 51 });
  });

  it('ida y vuelta conserva la posición', () => {
    const original = { x: -120, y: 340 };
    expect(fromFlowPosition(toFlowPosition(original))).toEqual(original);
  });
});

describe('IDs', () => {
  it('usa el siguiente número libre y no reutiliza huecos', () => {
    expect(nextId('C', [])).toBe('C1');
    expect(nextId('C', ['C1', 'C7', 'X3', 'Cx'])).toBe('C8');
  });
});

describe('calles', () => {
  it('una calle nueva mide la distancia del lienzo y tiene capacidad proporcional', () => {
    let [def, a] = addNode(EMPTY_DEFINITION, 'ENTRY', 0, 0);
    let b: string;
    [def, b] = addNode(def, 'EXIT', 300, 400);

    const [withEdge, edgeId] = addEdge(def, a, b);
    const edge = withEdge.edges.find((e) => e.id === edgeId)!;

    expect(edge.lengthMeters).toBe(500);
    expect(edge.storageCapacity).toBe(71); // 500 m / 7 m por vehículo
    expect(edge.closed).toBe(false);
  });

  it('no duplica calles en el mismo sentido ni crea bucles', () => {
    let [def, a] = addNode(EMPTY_DEFINITION, 'ENTRY', 0, 0);
    let b: string;
    [def, b] = addNode(def, 'EXIT', 100, 0);
    [def] = addEdge(def, a, b);

    expect(addEdge(def, a, b)[1]).toBeNull();
    expect(addEdge(def, a, a)[1]).toBeNull();
  });

  it('cambiar los carriles escala la capacidad en proporción', () => {
    let [def, a] = addNode(EMPTY_DEFINITION, 'ENTRY', 0, 0);
    let b: string;
    [def, b] = addNode(def, 'EXIT', 140, 0);
    let edgeId: string | null;
    [def, edgeId] = addEdge(def, a, b);
    expect(def.edges[0]!.storageCapacity).toBe(20);

    const doubled = setLanes(def, edgeId!, 2);
    expect(doubled.edges[0]).toMatchObject({ lanes: 2, storageCapacity: 40 });
    expect(setLanes(doubled, edgeId!, 1).edges[0]).toMatchObject({ lanes: 1, storageCapacity: 20 });
    expect(setLanes(def, edgeId!, 1)).toBe(def); // sin cambios, misma referencia
  });

  it('el sentido contrario copia las propiedades', () => {
    let [def, a] = addNode(EMPTY_DEFINITION, 'INTERSECTION', 0, 0);
    let b: string;
    [def, b] = addNode(def, 'INTERSECTION', 100, 0);
    let edgeId: string | null;
    [def, edgeId] = addEdge(def, a, b);
    def = { ...def, edges: def.edges.map((e) => ({ ...e, lanes: 3, closed: true })) };

    const [withReverse, reverseId] = addReverseEdge(def, edgeId!);
    const reverse = withReverse.edges.find((e) => e.id === reverseId)!;

    expect(reverse).toMatchObject({ from: b, to: a, lanes: 3, closed: true });
  });
});

describe('borrado en cascada', () => {
  it('borrar un nodo elimina sus calles, demandas, semáforo y giros prohibidos', () => {
    let [def, e] = addNode(EMPTY_DEFINITION, 'ENTRY', 0, 0);
    let i: string;
    let x: string;
    [def, i] = addNode(def, 'INTERSECTION', 100, 0);
    [def, x] = addNode(def, 'EXIT', 200, 0);
    let c1: string | null;
    let c2: string | null;
    [def, c1] = addEdge(def, e, i);
    [def, c2] = addEdge(def, i, x);
    [def] = addDemand(def, e, x);
    def = setSignal(def, i, { greenSeconds: 20, allRedSeconds: 2 });
    def = toggleProhibited(def, { from: c1!, to: c2! }, i);

    const result = removeNode(def, i);

    expect(result.nodes.map((n) => n.id)).toEqual([e, x]);
    expect(result.edges).toHaveLength(0);
    expect(result.signals).toHaveLength(0);
    expect(result.prohibitedMovements).toHaveLength(0);
    expect(removeNode(def, e).demands).toHaveLength(0);
  });

  it('cambiar el tipo de un nodo elimina demandas incompatibles', () => {
    let [def, e] = addNode(EMPTY_DEFINITION, 'ENTRY', 0, 0);
    let x: string;
    [def, x] = addNode(def, 'EXIT', 100, 0);
    [def] = addDemand(def, e, x);

    expect(changeNodeType(def, e, 'INTERSECTION').demands).toHaveLength(0);
    expect(changeNodeType(def, e, 'ENTRY').demands).toHaveLength(1);
  });
});

describe('semáforos', () => {
  it('la plantilla genera una fase por acceso, sin giro en U', () => {
    const def = setSignal(cross(), 'I1', { greenSeconds: 20, allRedSeconds: 3 });
    const plan = def.signals[0]!;

    expect(plan.phases).toHaveLength(8); // 4 accesos × (verde + todo-rojo)
    expect(plan.phases[0]!.greenMovements).toHaveLength(3); // recto, izquierda y derecha
    expect(plan.phases[1]).toEqual({ durationSeconds: 3, greenMovements: [] });
  });

  it('se regenera al quitar una calle y al prohibir un giro, conservando los tiempos', () => {
    let def = setSignal(cross(), 'I1', { greenSeconds: 25, allRedSeconds: 0 });
    const someIncoming = def.edges.find((e) => e.to === 'I1')!;

    def = removeEdge(def, someIncoming.id);
    expect(def.signals[0]!.phases).toHaveLength(3);
    expect(def.signals[0]!.phases[0]!.durationSeconds).toBe(25);

    const movement = movementsAt(def, 'I1')[0]!;
    def = toggleProhibited(def, movement, 'I1');
    const greens = def.signals[0]!.phases.flatMap((p) => p.greenMovements);
    expect(greens).not.toContainEqual(movement);
  });

  it('los giros posibles excluyen el giro en U', () => {
    expect(movementsAt(cross(), 'I1')).toHaveLength(12); // 4 entradas × 3 salidas
  });
});

describe('unidades', () => {
  it('convierte km/h y m/s', () => {
    expect(kmhToMps(36)).toBe(10);
    expect(mpsToKmh(13.888888)).toBe(50);
  });
});
