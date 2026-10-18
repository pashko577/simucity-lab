import type { MovementDef, PhaseDef, ScenarioDefinition, SignalDef } from '../api/types';

/**
 * Plan "una fase por acceso" con todo-rojo entre accesos. Espejo de SignalTemplates.splitPhasing del motor:
 * nunca tiene conflictos porque en cada fase solo avanza una calle de entrada.
 * Respeta el giro en U prohibido y los giros prohibidos del escenario.
 */
export function splitPhasing(def: ScenarioDefinition, nodeId: string, greenSeconds: number, allRedSeconds: number): SignalDef {
  const prohibited = new Set(def.prohibitedMovements.map((m) => `${m.from}>${m.to}`));
  const byId = (a: { id: string }, b: { id: string }) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0);
  const incoming = def.edges.filter((e) => e.to === nodeId).sort(byId);
  const outgoing = def.edges.filter((e) => e.from === nodeId).sort(byId);

  const phases: PhaseDef[] = [];
  for (const in_ of incoming) {
    const green: MovementDef[] = outgoing
      .filter((out) => out.to !== in_.from && !prohibited.has(`${in_.id}>${out.id}`))
      .map((out) => ({ from: in_.id, to: out.id }));
    if (green.length === 0) {
      continue;
    }
    phases.push({ durationSeconds: greenSeconds, greenMovements: green });
    if (allRedSeconds > 0) {
      phases.push({ durationSeconds: allRedSeconds, greenMovements: [] });
    }
  }
  return { nodeId, offsetSeconds: 0, phases };
}
