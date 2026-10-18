import type { Segment } from '../editor/geometry';
import type { PlaybackFrame } from './samples';

/** Calle a dibujar, en coordenadas del lienzo (las mismas que usa React Flow). */
export interface RenderEdge {
  id: string;
  segment: Segment;
  capacity: number;
}

export interface RenderScene {
  /** Tamaño del canvas en píxeles CSS. */
  width: number;
  height: number;
  devicePixelRatio: number;
  /** Transformación de React Flow: pantalla = lienzo × zoom + (x, y). */
  viewport: { x: number; y: number; zoom: number };
  edges: RenderEdge[];
  frame: PlaybackFrame;
}

/**
 * Dibuja una muestra de una ejecución. La vista 2D (CanvasRenderer) es la primera implementación;
 * la futura vista 3D (Three.js) implementará la misma interfaz con los mismos datos.
 */
export interface RunRenderer {
  render(scene: RenderScene): void;
}
