import { type Tool, useEditor } from './store';

const TOOLS: { tool: Tool; label: string; hint: string }[] = [
  { tool: 'select', label: 'Seleccionar', hint: 'Seleccionar y mover elementos' },
  { tool: 'ENTRY', label: '+ Entrada', hint: 'Clic en el lienzo: punto donde entran vehículos' },
  { tool: 'INTERSECTION', label: '+ Intersección', hint: 'Clic en el lienzo: cruce de calles' },
  { tool: 'EXIT', label: '+ Salida', hint: 'Clic en el lienzo: destino de los vehículos' },
  { tool: 'connect', label: 'Conectar calle', hint: 'Clic en el nodo de origen y luego en el de destino' },
];

export function Toolbar() {
  const tool = useEditor((s) => s.tool);
  const twoWay = useEditor((s) => s.twoWay);
  const connectFrom = useEditor((s) => s.connectFrom);
  const selection = useEditor((s) => s.selection);
  const { setTool, setTwoWay, deleteSelection } = useEditor.getState();

  const hint = tool === 'connect'
    ? connectFrom ? `Origen: ${connectFrom}. Clic en el nodo de destino (Esc para terminar).` : 'Clic en el nodo de origen.'
    : TOOLS.find((t) => t.tool === tool)?.hint;

  return (
    <div className="toolbar" role="toolbar" aria-label="Herramientas del editor">
      {TOOLS.map((t) => (
        <button key={t.tool} type="button" aria-pressed={tool === t.tool} title={t.hint}
          onClick={() => setTool(t.tool)}>
          {t.label}
        </button>
      ))}
      {tool === 'connect' && (
        <label className="checkbox">
          <input type="checkbox" checked={twoWay} onChange={(e) => setTwoWay(e.target.checked)} />
          Doble sentido
        </label>
      )}
      <button type="button" className="danger subtle" disabled={!selection} onClick={deleteSelection}
        title="Eliminar la selección (Supr)">
        Eliminar
      </button>
      <span className="toolbar-hint muted">{hint}</span>
    </div>
  );
}
