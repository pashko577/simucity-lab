import { beforeEach, describe, expect, it } from 'vitest';

import type { Scenario } from '../api/types';
import { EMPTY_DEFINITION } from './model';
import { useEditor } from './store';

const scenario: Scenario = {
  id: 's1',
  projectId: 'p1',
  name: 'Prueba',
  schemaVersion: 1,
  createdAt: '',
  updatedAt: '',
  definition: EMPTY_DEFINITION,
};

describe('store del editor', () => {
  beforeEach(() => useEditor.getState().load(scenario));

  it('con una herramienta de nodo, un clic en el lienzo crea el nodo y lo selecciona', () => {
    useEditor.getState().setTool('ENTRY');
    useEditor.getState().clickPane(10, 20);

    const { definition, selection, dirty } = useEditor.getState();
    expect(definition.nodes).toEqual([{ id: 'E1', type: 'ENTRY', x: 10, y: 20 }]);
    expect(selection).toEqual({ kind: 'node', id: 'E1' });
    expect(dirty).toBe(true);
  });

  it('conectar en doble sentido crea dos calles y encadena el origen', () => {
    const editor = useEditor.getState();
    editor.setTool('INTERSECTION');
    editor.clickPane(0, 0);
    editor.clickPane(100, 0);
    editor.clickPane(200, 0);
    editor.setTool('connect');
    editor.setTwoWay(true);

    editor.clickNode('I1');
    editor.clickNode('I2');
    expect(useEditor.getState().connectFrom).toBe('I2');
    editor.clickNode('I3');

    const edges = useEditor.getState().definition.edges.map((e) => `${e.from}>${e.to}`);
    expect(edges).toEqual(['I1>I2', 'I2>I1', 'I2>I3', 'I3>I2']);
  });

  it('en un solo sentido crea una calle', () => {
    const editor = useEditor.getState();
    editor.setTool('ENTRY');
    editor.clickPane(0, 0);
    editor.setTool('EXIT');
    editor.clickPane(100, 0);
    editor.setTool('connect');
    editor.setTwoWay(false);
    editor.clickNode('E1');
    editor.clickNode('S1');

    expect(useEditor.getState().definition.edges).toHaveLength(1);
  });

  it('borrar la selección elimina el nodo y sus calles', () => {
    const editor = useEditor.getState();
    editor.setTool('INTERSECTION');
    editor.clickPane(0, 0);
    editor.clickPane(100, 0);
    editor.setTool('connect');
    editor.clickNode('I1');
    editor.clickNode('I2');
    editor.setTool('select');
    editor.clickNode('I1');

    editor.deleteSelection();

    const { definition, selection } = useEditor.getState();
    expect(definition.nodes.map((n) => n.id)).toEqual(['I2']);
    expect(definition.edges).toHaveLength(0);
    expect(selection).toBeNull();
  });

  it('cargar un escenario reinicia el estado', () => {
    useEditor.getState().setTool('ENTRY');
    useEditor.getState().clickPane(0, 0);
    useEditor.getState().load(scenario);

    expect(useEditor.getState().definition.nodes).toHaveLength(0);
    expect(useEditor.getState().dirty).toBe(false);
    expect(useEditor.getState().tool).toBe('select');
  });
});
