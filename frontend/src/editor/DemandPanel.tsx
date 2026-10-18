import { useState } from 'react';

import type { ArrivalMode } from '../api/types';
import { NumberField } from '../ui/NumberField';
import * as model from './model';
import { useEditor } from './store';

export function DemandPanel() {
  const definition = useEditor((s) => s.definition);
  const apply = useEditor((s) => s.apply);
  const entries = definition.nodes.filter((n) => n.type === 'ENTRY');
  const exits = definition.nodes.filter((n) => n.type === 'EXIT');
  const [origin, setOrigin] = useState('');
  const [destination, setDestination] = useState('');

  const canAdd = entries.some((n) => n.id === origin) && exits.some((n) => n.id === destination);

  return (
    <section className="panel">
      <h2>Demanda</h2>
      {entries.length === 0 || exits.length === 0 ? (
        <p className="muted">Añade al menos una entrada y una salida para definir la demanda.</p>
      ) : (
        <div className="field-row align-end">
          <div className="field">
            <label htmlFor="demand-origin">Desde</label>
            <select id="demand-origin" value={origin} onChange={(e) => setOrigin(e.target.value)}>
              <option value="">—</option>
              {entries.map((n) => <option key={n.id} value={n.id}>{n.id}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="demand-destination">Hacia</label>
            <select id="demand-destination" value={destination} onChange={(e) => setDestination(e.target.value)}>
              <option value="">—</option>
              {exits.map((n) => <option key={n.id} value={n.id}>{n.id}</option>)}
            </select>
          </div>
          <button type="button" disabled={!canAdd}
            onClick={() => apply((d) => model.addDemand(d, origin, destination)[0])}>
            Añadir
          </button>
        </div>
      )}

      <ul className="demand-list">
        {definition.demands.map((d) => (
          <li key={d.id} className="demand-item">
            <strong>{d.id}</strong> <span>{d.origin} → {d.destination}</span>
            <div className="field-row align-end">
              <NumberField label="Tasa" unit="veh/min" min={0} max={120} value={d.vehiclesPerMinute}
                onCommit={(v) => apply((def) => model.updateDemand(def, d.id, { vehiclesPerMinute: v }))} />
              <div className="field">
                <label htmlFor={`mode-${d.id}`}>Llegadas</label>
                <select id={`mode-${d.id}`} value={d.mode}
                  onChange={(e) => apply((def) => model.updateDemand(def, d.id, { mode: e.target.value as ArrivalMode }))}>
                  <option value="POISSON">Aleatorias (Poisson)</option>
                  <option value="PERIODIC">Regulares</option>
                </select>
              </div>
              <button type="button" className="danger subtle" aria-label={`Eliminar demanda ${d.id}`}
                onClick={() => apply((def) => model.removeDemand(def, d.id))}>
                ✕
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
