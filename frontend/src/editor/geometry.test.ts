import { describe, expect, it } from 'vitest';

import { offsetToRight, roadSegment, trim } from './geometry';

describe('offsetToRight (pantalla, eje Y hacia abajo)', () => {
  it('hacia el este, la derecha es hacia abajo (sur)', () => {
    expect(offsetToRight({ x1: 0, y1: 0, x2: 100, y2: 0 }, 6)).toEqual({ x1: 0, y1: 6, x2: 100, y2: 6 });
  });

  it('los dos sentidos de una calle quedan a lados opuestos', () => {
    const ida = offsetToRight({ x1: 0, y1: 0, x2: 100, y2: 0 }, 6);
    const vuelta = offsetToRight({ x1: 100, y1: 0, x2: 0, y2: 0 }, 6);
    expect(ida.y1).toBe(6);
    expect(vuelta.y1).toBe(-6);
  });

  it('un segmento de longitud 0 no se modifica', () => {
    const point = { x1: 5, y1: 5, x2: 5, y2: 5 };
    expect(offsetToRight(point)).toEqual(point);
  });
});

describe('trim', () => {
  it('recorta ambos extremos', () => {
    expect(trim({ x1: 0, y1: 0, x2: 100, y2: 0 }, 10)).toEqual({ x1: 10, y1: 0, x2: 90, y2: 0 });
  });

  it('no recorta segmentos más cortos que los dos nodos', () => {
    const short = { x1: 0, y1: 0, x2: 15, y2: 0 };
    expect(trim(short, 10)).toEqual(short);
  });
});

describe('roadSegment', () => {
  it('combina desplazamiento y recorte', () => {
    const s = roadSegment({ x: 0, y: 0 }, { x: 100, y: 0 });
    expect(s.y1).toBeGreaterThan(0);
    expect(s.x1).toBeGreaterThan(0);
    expect(s.x2).toBeLessThan(100);
  });
});
