import { create } from 'zustand';

import type { NodeType, SamplesPayload, Scenario, ScenarioDefinition, ValidationIssue } from '../api/types';
import * as model from './model';

export type Tool = 'select' | 'connect' | Extract<NodeType, 'INTERSECTION' | 'ENTRY' | 'EXIT'>;

export type Selection = { kind: 'node'; id: string } | { kind: 'edge'; id: string } | null;

export interface Playback {
  runId: string;
  payload: SamplesPayload;
  timeSeconds: number;
  playing: boolean;
  /** Segundos simulados por segundo real. */
  speed: number;
}

interface EditorState {
  scenarioId: string | null;
  projectId: string | null;
  name: string;
  /** Fuente de verdad: lo que se guarda en la API. React Flow solo la muestra. */
  definition: ScenarioDefinition;
  dirty: boolean;
  tool: Tool;
  twoWay: boolean;
  connectFrom: string | null;
  selection: Selection;
  issues: ValidationIssue[];
  playback: Playback | null;

  load(scenario: Scenario): void;
  markSaved(scenario: Scenario): void;
  setName(name: string): void;
  setTool(tool: Tool): void;
  setTwoWay(twoWay: boolean): void;
  select(selection: Selection): void;
  setIssues(issues: ValidationIssue[]): void;
  /** Aplica una operación pura de model.ts y marca el escenario como modificado. */
  apply(change: (definition: ScenarioDefinition) => ScenarioDefinition): void;
  /** Clic en el lienzo vacío (coordenadas del motor). */
  clickPane(x: number, y: number): void;
  clickNode(nodeId: string): void;
  deleteSelection(): void;
  setPlayback(playback: Playback | null): void;
  updatePlayback(changes: Partial<Omit<Playback, 'runId' | 'payload'>>): void;
}

export const useEditor = create<EditorState>()((set, get) => ({
  scenarioId: null,
  projectId: null,
  name: '',
  definition: model.EMPTY_DEFINITION,
  dirty: false,
  tool: 'select',
  twoWay: true,
  connectFrom: null,
  selection: null,
  issues: [],
  playback: null,

  load: (scenario) =>
    set({
      scenarioId: scenario.id,
      projectId: scenario.projectId,
      name: scenario.name,
      definition: scenario.definition,
      dirty: false,
      tool: 'select',
      connectFrom: null,
      selection: null,
      issues: [],
      playback: null,
    }),

  markSaved: (scenario) => set({ name: scenario.name, dirty: false }),

  setName: (name) => set({ name, dirty: true }),

  setTool: (tool) => set({ tool, connectFrom: null }),

  setTwoWay: (twoWay) => set({ twoWay }),

  select: (selection) => set({ selection }),

  setIssues: (issues) => set({ issues }),

  apply: (change) => set((state) => ({ definition: change(state.definition), dirty: true })),

  clickPane: (x, y) => {
    const { tool, definition } = get();
    if (tool === 'INTERSECTION' || tool === 'ENTRY' || tool === 'EXIT') {
      const [next, id] = model.addNode(definition, tool, x, y);
      set({ definition: next, dirty: true, selection: { kind: 'node', id } });
    } else {
      set({ selection: null, connectFrom: null });
    }
  },

  clickNode: (nodeId) => {
    const { tool, connectFrom, twoWay, definition } = get();
    if (tool !== 'connect') {
      set({ selection: { kind: 'node', id: nodeId } });
      return;
    }
    if (!connectFrom || connectFrom === nodeId) {
      set({ connectFrom: nodeId });
      return;
    }
    let [next, edgeId] = model.addEdge(definition, connectFrom, nodeId);
    if (twoWay) {
      [next] = model.addEdge(next, nodeId, connectFrom);
    }
    set({
      definition: next,
      dirty: next !== definition || get().dirty,
      // Encadenar: el destino pasa a ser el origen de la siguiente calle.
      connectFrom: nodeId,
      selection: edgeId ? { kind: 'edge', id: edgeId } : get().selection,
    });
  },

  deleteSelection: () => {
    const { selection, definition } = get();
    if (!selection) {
      return;
    }
    const next = selection.kind === 'node'
      ? model.removeNode(definition, selection.id)
      : model.removeEdge(definition, selection.id);
    set({ definition: next, dirty: true, selection: null });
  },

  setPlayback: (playback) => set({ playback }),

  updatePlayback: (changes) =>
    set((state) => (state.playback ? { playback: { ...state.playback, ...changes } } : {})),
}));
