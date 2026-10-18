import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';

import { EMPTY_DEFINITION } from './model';
import { PropertiesPanel } from './PropertiesPanel';
import { useEditor } from './store';

function loadNetwork() {
  const editor = useEditor.getState();
  editor.load({ id: 's1', projectId: 'p1', name: 'x', schemaVersion: 1, createdAt: '', updatedAt: '', definition: EMPTY_DEFINITION });
  editor.setTool('INTERSECTION');
  editor.clickPane(0, 0);
  editor.clickPane(100, 0);
  editor.setTool('connect');
  editor.setTwoWay(false);
  editor.clickNode('I1');
  editor.clickNode('I2');
  editor.setTool('select');
}

describe('PropertiesPanel', () => {
  beforeEach(loadNetwork);

  it('muestra la velocidad en km/h y la guarda en m/s', async () => {
    useEditor.getState().select({ kind: 'edge', id: 'C1' });
    render(<PropertiesPanel />);

    const speed = screen.getByLabelText(/Velocidad/);
    expect(speed).toHaveValue(50);

    await userEvent.clear(speed);
    await userEvent.type(speed, '36{Enter}');

    expect(useEditor.getState().definition.edges[0]!.speedLimitMps).toBe(10);
  });

  it('un valor inválido no se guarda y el campo vuelve al valor anterior', async () => {
    useEditor.getState().select({ kind: 'edge', id: 'C1' });
    render(<PropertiesPanel />);

    const lanes = screen.getByLabelText(/Carriles/);
    await userEvent.clear(lanes);
    await userEvent.type(lanes, '0');
    await userEvent.tab();

    expect(useEditor.getState().definition.edges[0]!.lanes).toBe(1);
    expect(lanes).toHaveValue(1);
  });

  it('crea el sentido contrario y lo selecciona', async () => {
    useEditor.getState().select({ kind: 'edge', id: 'C1' });
    render(<PropertiesPanel />);

    await userEvent.click(screen.getByRole('button', { name: 'Crear sentido contrario' }));

    const { definition, selection } = useEditor.getState();
    expect(definition.edges.map((e) => `${e.from}>${e.to}`)).toEqual(['I1>I2', 'I2>I1']);
    expect(selection).toEqual({ kind: 'edge', id: 'C2' });
  });

  it('activar el semáforo sin calles de salida lo conserva y avisa; al conectar, genera las fases', async () => {
    useEditor.getState().select({ kind: 'node', id: 'I2' });
    render(<PropertiesPanel />);

    await userEvent.click(screen.getByLabelText(/Controlar con semáforo/));

    expect(useEditor.getState().definition.signals).toHaveLength(1);
    expect(screen.getByLabelText(/Verde por acceso/)).toHaveValue(30);
    expect(screen.getByText(/Conecta calles de entrada y salida/)).toBeInTheDocument();

    const editor = useEditor.getState();
    editor.setTool('EXIT');
    editor.clickPane(200, 0);
    editor.setTool('connect');
    editor.clickNode('I2');
    editor.clickNode('S1');

    const phases = useEditor.getState().definition.signals[0]!.phases;
    expect(phases[0]).toEqual({ durationSeconds: 30, greenMovements: [{ from: 'C1', to: 'C2' }] });
  });
});
