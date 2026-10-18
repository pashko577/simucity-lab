/** Geometría en coordenadas de pantalla (eje Y hacia abajo), compartida por el editor y la animación. */

export interface Segment {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

/** Separación entre los dos sentidos de una calle, en unidades del lienzo. */
export const LANE_OFFSET = 6;

/** Radio visual de un nodo: las calles empiezan y terminan en su borde, no en el centro. */
export const NODE_RADIUS = 14;

/**
 * Desplaza el segmento hacia la derecha del sentido de circulación (circulación por la derecha).
 * Con el eje Y hacia abajo, la derecha de la dirección (dx, dy) es (-dy, dx).
 */
export function offsetToRight(segment: Segment, offset = LANE_OFFSET): Segment {
  const dx = segment.x2 - segment.x1;
  const dy = segment.y2 - segment.y1;
  const length = Math.hypot(dx, dy);
  if (length === 0) {
    return segment;
  }
  const ox = (-dy / length) * offset;
  const oy = (dx / length) * offset;
  return { x1: segment.x1 + ox, y1: segment.y1 + oy, x2: segment.x2 + ox, y2: segment.y2 + oy };
}

/** Recorta los extremos para que la calle no se dibuje dentro de los nodos. */
export function trim(segment: Segment, amount = NODE_RADIUS): Segment {
  const dx = segment.x2 - segment.x1;
  const dy = segment.y2 - segment.y1;
  const length = Math.hypot(dx, dy);
  if (length <= 2 * amount) {
    return segment;
  }
  const ux = dx / length;
  const uy = dy / length;
  return {
    x1: segment.x1 + ux * amount,
    y1: segment.y1 + uy * amount,
    x2: segment.x2 - ux * amount,
    y2: segment.y2 - uy * amount,
  };
}

/** Segmento visible de una calle entre dos centros de nodo. */
export function roadSegment(fromCenter: { x: number; y: number }, toCenter: { x: number; y: number }): Segment {
  return trim(offsetToRight({ x1: fromCenter.x, y1: fromCenter.y, x2: toCenter.x, y2: toCenter.y }));
}

export function pointAt(segment: Segment, fraction: number): { x: number; y: number } {
  return {
    x: segment.x1 + (segment.x2 - segment.x1) * fraction,
    y: segment.y1 + (segment.y2 - segment.y1) * fraction,
  };
}
