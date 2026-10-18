import { lazy, Suspense } from 'react';
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router';

import { LoginPage } from './pages/LoginPage';
import { ProjectPage } from './pages/ProjectPage';
import { ProjectsPage } from './pages/ProjectsPage';
import { RequireAuth } from './ui/RequireAuth';

// El editor (React Flow) es la parte más pesada: se descarga solo al abrir un escenario.
const EditorPage = lazy(() => import('./pages/EditorPage').then((m) => ({ default: m.EditorPage })));

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      { path: '/projects', element: <ProjectsPage /> },
      { path: '/projects/:projectId', element: <ProjectPage /> },
      {
        path: '/scenarios/:scenarioId',
        element: (
          <Suspense fallback={<p className="page-message">Cargando editor…</p>}>
            <EditorPage />
          </Suspense>
        ),
      },
    ],
  },
  { path: '*', element: <Navigate to="/projects" replace /> },
]);

export function App() {
  return <RouterProvider router={router} />;
}
