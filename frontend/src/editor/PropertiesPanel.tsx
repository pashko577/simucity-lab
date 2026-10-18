import type { EdgeDef, NodeDef, NodeType } from '../api/types';
import { NODE_TYPE_LABEL } from '../ui/labels';
import { NumberField } from '../ui/NumberField';
import * as model from './model';
import { useEditor } from './store';

export function PropertiesPanel() {
  const selection = useEditor((s) => s.selection);
  const definition = useEditor((s) => s.definition);

  if (!selection) {
    return (
      <section className="panel">
        <h2>Propiedades</h2>
        <p className="muted">
          Selecciona una intersección o una calle para editarla. Usa las herramientas de arriba para dibujar la red:
          entradas, intersecciones y salidas, y luego conéctalas con calles.
        </p>
      </section>
    );
  }
  if (selection.kind === 'node') {
    const node = definition.nodes.find((n) => n.id === selection.id);
    return node ? <NodeProperties node={node} /> : null;
  }
  const edge = definition.edges.find((e) => e.id === selection.id);
  return edge ? <EdgeProperties edge={edge} /> : null;
}

function NodeProperties({ node }: { node: NodeDef }) {
  const definition = useEditor((s) => s.definition);
  const apply = useEditor((s) => s.apply);
  const signal = definition.signals.find((s) => s.nodeId === node.id);
  const settings = signal ? model.signalSettings(signal) : null;
  const movements = model.movementsAt(definition, node.id);

  return (
    <section className="panel">
      <h2>{NODE_TYPE_LABEL[node.type]} {node.id}</h2>
      <div className="field">
        <label htmlFor="node-type">Tipo</label>
        <select id="node-type" value={node.type}
          onChange={(e) => apply((d) => model.changeNodeType(d, node.id, e.target.value as NodeType))}>
          {(Object.keys(NODE_TYPE_LABEL) as NodeType[]).map((t) => (
            <option key={t} value={t}>{NODE_TYPE_LABEL[t]}</option>
          ))}
        </select>
      </div>
      <p className="muted small">Posición: x = {node.x} m, y = {node.y} m</p>

      {node.type === 'INTERSECTION' && (
        <>
          <h3>Semáforo</h3>
          <label className="checkbox">
            <input type="checkbox" checked={!!signal}
              onChange={(e) => apply((d) => model.setSignal(d, node.id, e.target.checked ? model.DEFAULT_SIGNAL : null))} />
            Controlar con semáforo (una fase por acceso)
          </label>
          {settings && (
            <div className="field-row">
              <NumberField label="Verde por acceso" unit="s" min={1} max={300} value={settings.greenSeconds}
                onCommit={(v) => apply((d) => model.setSignal(d, node.id, { ...settings, greenSeconds: v }))} />
              <NumberField label="Todo rojo" unit="s" min={0} max={30} value={settings.allRedSeconds}
                onCommit={(v) => apply((d) => model.setSignal(d, node.id, { ...settings, allRedSeconds: v }))} />
            </div>
          )}
          {signal && signal.phases.length === 0 && (
            <p className="muted small">Conecta calles de entrada y salida para generar las fases.</p>
          )}

          {movements.length > 0 && (
            <>
              <h3>Giros permitidos</h3>
              <ul className="movement-list">
                {movements.map((m) => (
                  <li key={`${m.from}>${m.to}`}>
                    <label className="checkbox">
                      <input type="checkbox" checked={!model.isProhibited(definition, m)}
                        onChange={() => apply((d) => model.toggleProhibited(d, m, node.id))} />
                      {m.from} → {m.to}
                    </label>
                  </li>
                ))}
              </ul>
            </>
          )}
        </>
      )}
    </section>
  );
}

function EdgeProperties({ edge }: { edge: EdgeDef }) {
  const definition = useEditor((s) => s.definition);
  const apply = useEditor((s) => s.apply);
  const select = useEditor((s) => s.select);
  const update = (changes: Partial<EdgeDef>) => apply((d) => model.updateEdge(d, edge.id, changes));
  const reverse = definition.edges.find((e) => e.from === edge.to && e.to === edge.from);

  return (
    <section className="panel">
      <h2>Calle {edge.id}</h2>
      <p className="muted small">De {edge.from} hacia {edge.to} (un solo sentido)</p>
      <div className="field-row">
        <NumberField label="Longitud" unit="m" min={1} value={edge.lengthMeters}
          onCommit={(v) => update({ lengthMeters: v })} />
        <NumberField label="Carriles" integer min={1} max={8} value={edge.lanes}
          onCommit={(v) => apply((d) => model.setLanes(d, edge.id, v))} />
      </div>
      <div className="field-row">
        <NumberField label="Velocidad" unit="km/h" min={1} max={150} value={model.mpsToKmh(edge.speedLimitMps)}
          onCommit={(v) => update({ speedLimitMps: model.kmhToMps(v) })} />
        <NumberField label="Capacidad" unit="veh" integer min={1} value={edge.storageCapacity}
          onCommit={(v) => update({ storageCapacity: v })} />
      </div>
      <label className="checkbox">
        <input type="checkbox" checked={edge.closed} onChange={(e) => update({ closed: e.target.checked })} />
        Calle cerrada al tráfico
      </label>
      <div className="button-row">
        {reverse ? (
          <button type="button" className="subtle" onClick={() => select({ kind: 'edge', id: reverse.id })}>
            Ver sentido contrario ({reverse.id})
          </button>
        ) : (
          <button type="button" className="subtle" onClick={() => {
            let created: string | null = null;
            apply((d) => {
              const [next, id] = model.addReverseEdge(d, edge.id);
              created = id;
              return next;
            });
            if (created) {
              select({ kind: 'edge', id: created });
            }
          }}>
            Crear sentido contrario
          </button>
        )}
      </div>
      <p className="muted small">
        La capacidad es cuántos vehículos caben en la calle; por defecto, uno cada 7 m por carril, y se ajusta
        en proporción al cambiar los carriles. Son valores pedagógicos, no mediciones reales.
      </p>
    </section>
  );
}
