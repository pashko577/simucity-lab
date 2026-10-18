import '@xyflow/react/dist/style.css';

import { ReactFlowProvider } from '@xyflow/react';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';

import { scenariosApi } from '../api/endpoints';
import { DemandPanel } from '../editor/DemandPanel';
import { EditorCanvas } from '../editor/EditorCanvas';
import { PropertiesPanel } from '../editor/PropertiesPanel';
import { RunPanel } from '../editor/RunPanel';
import { useEditor } from '../editor/store';
import { Toolbar } from '../editor/Toolbar';
import { useSaveScenario } from '../editor/useSaveScenario';
import { ValidationPanel } from '../editor/ValidationPanel';
import { PlaybackControls } from '../playback/PlaybackControls';
import { AppHeader } from '../ui/AppHeader';
import { ErrorMessage } from '../ui/ErrorMessage';

export function EditorPage() {
  const { scenarioId = '' } = useParams();
  const loadedId = useEditor((s) => s.scenarioId);
  const projectId = useEditor((s) => s.projectId);
  const name = useEditor((s) => s.name);
  const dirty = useEditor((s) => s.dirty);
  const save = useSaveScenario();
  const [error, setError] = useState<unknown>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    scenariosApi.get(scenarioId).then(useEditor.getState().load).catch(setError);
  }, [scenarioId]);

  // Teclado: Supr/Retroceso borra la selección; Esc vuelve a "Seleccionar"; Ctrl+S guarda.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const typing = event.target instanceof HTMLElement && event.target.closest('input, select, textarea');
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
        event.preventDefault();
        void save();
      } else if (!typing && (event.key === 'Delete' || event.key === 'Backspace')) {
        useEditor.getState().deleteSelection();
      } else if (event.key === 'Escape') {
        useEditor.getState().setTool('select');
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [save]);

  // Aviso del navegador al cerrar la pestaña con cambios sin guardar.
  useEffect(() => {
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (useEditor.getState().dirty) {
        event.preventDefault();
      }
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, []);

  const saveNow = async () => {
    setSaving(true);
    setError(null);
    await save().catch(setError);
    setSaving(false);
  };

  if (loadedId !== scenarioId) {
    return (
      <>
        <AppHeader />
        {error ? <ErrorMessage error={error} /> : <p className="page-message">Cargando escenario…</p>}
      </>
    );
  }

  return (
    <ReactFlowProvider>
      <div className="editor-layout">
        <AppHeader>
          {projectId && <Link to={`/projects/${projectId}`}>← Proyecto</Link>}
          <input className="scenario-name" aria-label="Nombre del escenario" maxLength={120} value={name}
            onChange={(e) => useEditor.getState().setName(e.target.value)} />
          <button type="button" className="primary" onClick={saveNow} disabled={!dirty || saving}>
            {saving ? 'Guardando…' : dirty ? 'Guardar' : 'Guardado'}
          </button>
        </AppHeader>
        <ErrorMessage error={error} />
        <Toolbar />
        <div className="editor-body">
          <div className="editor-main">
            <EditorCanvas />
            <PlaybackControls />
          </div>
          <aside className="editor-sidebar">
            <PropertiesPanel />
            <DemandPanel />
            <ValidationPanel />
            <RunPanel />
          </aside>
        </div>
      </div>
    </ReactFlowProvider>
  );
}
