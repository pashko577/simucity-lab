import { useEffect } from 'react';

import { useEditor } from '../editor/store';
import { formatClock, lastTime } from './samples';

const SPEEDS = [1, 5, 20, 60];

/**
 * Reloj de reproducción. Avanza el tiempo simulado con requestAnimationFrame; pausar o cambiar la velocidad
 * nunca altera los resultados, solo qué muestra se ve (sección 5.3).
 */
export function PlaybackControls() {
  const playback = useEditor((s) => s.playback);
  const { updatePlayback, setPlayback } = useEditor.getState();
  const end = playback ? lastTime(playback.payload) : 0;

  useEffect(() => {
    if (!playback?.playing) {
      return;
    }
    let previous = performance.now();
    let frame = requestAnimationFrame(function tick(now) {
      const elapsed = (now - previous) / 1000;
      previous = now;
      const current = useEditor.getState().playback;
      if (!current) {
        return;
      }
      const next = current.timeSeconds + elapsed * current.speed;
      if (next >= end) {
        updatePlayback({ timeSeconds: end, playing: false });
        return;
      }
      updatePlayback({ timeSeconds: next });
      frame = requestAnimationFrame(tick);
    });
    return () => cancelAnimationFrame(frame);
  }, [playback?.playing, end, updatePlayback]);

  if (!playback) {
    return null;
  }

  return (
    <div className="playback-controls">
      <button type="button" onClick={() => updatePlayback({
        playing: !playback.playing,
        timeSeconds: playback.timeSeconds >= end ? 0 : playback.timeSeconds,
      })}>
        {playback.playing ? 'Pausa' : 'Reproducir'}
      </button>
      <input type="range" aria-label="Tiempo simulado" min={0} max={end} step={0.5} value={playback.timeSeconds}
        onChange={(e) => updatePlayback({ timeSeconds: Number(e.target.value), playing: false })} />
      <span className="clock">{formatClock(playback.timeSeconds)} / {formatClock(end)}</span>
      <select aria-label="Velocidad" value={playback.speed}
        onChange={(e) => updatePlayback({ speed: Number(e.target.value) })}>
        {SPEEDS.map((s) => <option key={s} value={s}>×{s}</option>)}
      </select>
      <button type="button" className="subtle" onClick={() => setPlayback(null)}>Ocultar</button>
      <div className="legend" aria-label="Leyenda">
        <span className="legend-bar" /> <span className="muted small">libre → llena</span>
      </div>
    </div>
  );
}
