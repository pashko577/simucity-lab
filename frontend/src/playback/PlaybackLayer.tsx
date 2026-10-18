import { useViewport } from '@xyflow/react';
import { useEffect, useMemo, useRef, useState } from 'react';

import { roadSegment } from '../editor/geometry';
import { toFlowPosition } from '../editor/model';
import { useEditor } from '../editor/store';
import { CanvasRenderer } from './CanvasRenderer';
import type { RenderEdge } from './Renderer';
import { frameAt } from './samples';

/**
 * Capa <canvas> encima de React Flow que dibuja la muestra actual. Sigue el zoom y el desplazamiento del
 * lienzo y no captura el ratón: se puede seguir navegando mientras se reproduce.
 */
export function PlaybackLayer() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const viewport = useViewport();
  const definition = useEditor((s) => s.definition);
  const playback = useEditor((s) => s.playback);

  const renderEdges = useMemo<RenderEdge[]>(() => {
    const nodes = new Map(definition.nodes.map((n) => [n.id, toFlowPosition(n)]));
    return definition.edges.flatMap((e) => {
      const from = nodes.get(e.from);
      const to = nodes.get(e.to);
      return from && to ? [{ id: e.id, segment: roadSegment(from, to), capacity: e.storageCapacity }] : [];
    });
  }, [definition]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const parent = canvas?.parentElement;
    if (!canvas || !parent) {
      return;
    }
    const observer = new ResizeObserver(([entry]) => {
      if (entry) {
        setSize({ width: entry.contentRect.width, height: entry.contentRect.height });
      }
    });
    observer.observe(parent);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx || !playback || size.width === 0) {
      return;
    }
    const dpr = window.devicePixelRatio || 1;
    canvas.width = Math.round(size.width * dpr);
    canvas.height = Math.round(size.height * dpr);
    const frame = frameAt(playback.payload, playback.timeSeconds);
    if (frame) {
      new CanvasRenderer(ctx).render({
        width: size.width,
        height: size.height,
        devicePixelRatio: dpr,
        viewport,
        edges: renderEdges,
        frame,
      });
    }
  }, [playback, viewport, renderEdges, size]);

  return (
    <canvas
      ref={canvasRef}
      className="playback-layer"
      style={{ width: size.width, height: size.height }}
      aria-label="Animación de la ejecución"
    />
  );
}
