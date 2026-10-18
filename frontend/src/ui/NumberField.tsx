import { useEffect, useId, useState } from 'react';

interface NumberFieldProps {
  label: string;
  unit?: string;
  value: number;
  min?: number;
  max?: number;
  step?: number;
  integer?: boolean;
  onCommit: (value: number) => void;
}

/**
 * Campo numérico que guarda el texto mientras se escribe y solo confirma un número válido al salir del campo
 * o pulsar Enter. Si el valor no es válido, vuelve al anterior.
 */
export function NumberField({ label, unit, value, min, max, step, integer, onCommit }: NumberFieldProps) {
  const id = useId();
  const [text, setText] = useState(String(value));

  useEffect(() => setText(String(value)), [value]);

  const commit = () => {
    const parsed = Number(text.replace(',', '.'));
    const valid = text.trim() !== '' && Number.isFinite(parsed)
      && (min === undefined || parsed >= min) && (max === undefined || parsed <= max)
      && (!integer || Number.isInteger(parsed));
    if (valid && parsed !== value) {
      onCommit(parsed);
    } else if (!valid) {
      setText(String(value));
    }
  };

  return (
    <div className="field">
      <label htmlFor={id}>{label}{unit && <span className="unit"> ({unit})</span>}</label>
      <input
        id={id}
        type="number"
        inputMode="decimal"
        value={text}
        min={min}
        max={max}
        step={step ?? (integer ? 1 : 'any')}
        onChange={(event) => setText(event.target.value)}
        onBlur={commit}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            commit();
          }
        }}
      />
    </div>
  );
}
