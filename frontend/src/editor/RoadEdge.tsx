import { BaseEdge, type Edge, type EdgeProps, type InternalNode, useInternalNode } from '@xyflow/react';

import { roadSegment } from './geometry';

export type RoadEdgeData = {
  closed: boolean;
  hasIssue: boolean;
};

export type RoadFlowEdge = Edge<RoadEdgeData, 'road'>;

/**
 * Con nodeOrigin = [0.5, 0.5] la posición del nodo ES su centro (y se actualiza durante el arrastre).
 * La capa de animación usa la misma convención, así que ambas dibujan la calle en el mismo sitio.
 */
function center(node: InternalNode): { x: number; y: number } {
  return node.position;
}

/**
 * Calle dirigida: recta entre los centros de los nodos, desplazada hacia la derecha de su sentido para que
 * los dos sentidos de una calle se vean como carriles separados.
 */
export function RoadEdge({ id, source, target, selected, data, markerEnd }: EdgeProps<RoadFlowEdge>) {
  const from = useInternalNode(source);
  const to = useInternalNode(target);
  if (!from || !to) {
    return null;
  }
  const s = roadSegment(center(from), center(to));
  const classes = ['road-edge', data?.closed ? 'is-closed' : '', data?.hasIssue ? 'has-issue' : '', selected ? 'is-selected' : '']
    .join(' ');

  return (
    <BaseEdge
      id={id}
      path={`M ${s.x1} ${s.y1} L ${s.x2} ${s.y2}`}
      className={classes}
      markerEnd={markerEnd}
      interactionWidth={14}
    />
  );
}
