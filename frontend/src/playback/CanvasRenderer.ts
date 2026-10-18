import { pointAt } from '../editor/geometry';
import type { RenderScene, RunRenderer } from './Renderer';
import { occupancyColor, vehicleFractions } from './samples';

/** Vista 2D: cada calle se colorea por ocupación y cada vehículo es un punto. */
export class CanvasRenderer implements RunRenderer {
  private readonly ctx: CanvasRenderingContext2D;

  constructor(ctx: CanvasRenderingContext2D) {
    this.ctx = ctx;
  }

  render(scene: RenderScene): void {
    const { ctx } = this;
    const { width, height, devicePixelRatio: dpr, viewport, edges, frame } = scene;

    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, width * dpr, height * dpr);
    ctx.setTransform(viewport.zoom * dpr, 0, 0, viewport.zoom * dpr, viewport.x * dpr, viewport.y * dpr);

    for (const edge of edges) {
      const state = frame.edges.get(edge.id);
      if (!state) {
        continue; // Calle añadida después de la ejecución: no tiene datos.
      }
      const { segment } = edge;
      ctx.lineCap = 'round';
      ctx.lineWidth = 5;
      ctx.strokeStyle = occupancyColor(state.vehicles / Math.max(1, edge.capacity));
      ctx.globalAlpha = 0.85;
      ctx.beginPath();
      ctx.moveTo(segment.x1, segment.y1);
      ctx.lineTo(segment.x2, segment.y2);
      ctx.stroke();

      ctx.globalAlpha = 1;
      for (const fraction of vehicleFractions(state.vehicles, state.queued, edge.capacity)) {
        const p = pointAt(segment, fraction);
        ctx.beginPath();
        ctx.arc(p.x, p.y, 2.4, 0, Math.PI * 2);
        ctx.fillStyle = '#1b1f24';
        ctx.fill();
      }
    }
  }
}
