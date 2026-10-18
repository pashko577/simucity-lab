import { useCallback } from 'react';

import { scenariosApi } from '../api/endpoints';
import { useEditor } from './store';

/** Guarda el escenario si tiene cambios. Validar y ejecutar siempre trabajan con la versión guardada. */
export function useSaveScenario(): () => Promise<void> {
  return useCallback(async () => {
    const { scenarioId, name, definition, dirty, markSaved } = useEditor.getState();
    if (!scenarioId || !dirty) {
      return;
    }
    const saved = await scenariosApi.save(scenarioId, name.trim() || 'Sin nombre', definition);
    // Solo se marca como guardado si nadie lo modificó mientras se enviaba.
    if (useEditor.getState().definition === definition) {
      markSaved(saved);
    }
  }, []);
}
