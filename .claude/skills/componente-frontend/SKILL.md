---
name: componente-frontend
description: Convenciones del frontend de SimuCity Lab (frontend/, React 19 + TypeScript + Vite + React Flow + Zustand) - estructura, cliente de la API con sesión y CSRF, editor de redes, conversión de coordenadas (eje Y), animación en Canvas y pruebas con Vitest. Úsala al crear o modificar pantallas, componentes, el editor de escenarios, la reproducción de ejecuciones o el cliente de la API.
---

# Frontend — SimuCity Lab

Stack (versiones nuevas: verificar APIs antes de usarlas): React 19, TypeScript 7, Vite 8, React Flow (`@xyflow/react` 12), Zustand 5, React Router 8, Vitest 5 + Testing Library.

## Estructura

```
frontend/src/
  api/        client.ts (fetch + sesión + CSRF + errores), types.ts (espejo de los DTO del backend), endpoints.ts
  editor/     model.ts (conversiones puras), store.ts (Zustand), signalTemplates.ts, componentes del editor
  playback/   samples.ts (lógica pura), Renderer.ts (interfaz), CanvasRenderer.ts, PlaybackLayer.tsx
  pages/      una página por ruta
  ui/         componentes genéricos y textos (labels.ts)
```

Lógica en funciones puras (`model.ts`, `samples.ts`, `signalTemplates.ts`) y componentes delgados. Lo puro se prueba sin DOM.

## Reglas del editor (obligatorias)

1. **El estado es nuestro, no de React Flow.** La fuente de verdad es la `ScenarioDefinition` del store de Zustand; los nodos y aristas de React Flow se **derivan** de ella. Lo que se guarda en la API es exactamente esa definición.
2. **Eje Y invertido.** El motor usa Y hacia arriba (circulación por la derecha en la detección de conflictos); la pantalla usa Y hacia abajo. Toda conversión pasa por `toFlowPosition` / `fromFlowPosition` de `editor/model.ts`. Nunca convertir a mano en un componente.
3. **Calles de doble sentido = 2 aristas.** `RoadEdge` dibuja cada sentido desplazado hacia **su derecha** para que no se superpongan.
4. **Borrado en cascada:** borrar un nodo borra sus calles, las demandas que lo usan, su semáforo y los giros prohibidos afectados. Al cambiar las calles de una intersección con semáforo, se regenera su plan (`regenerateSignal`).
5. Unidades visibles siempre: m, km/h (convertidos a m/s al guardar), veh, veh/min, s.

## API

- Todo pasa por `api/client.ts`: `credentials: 'same-origin'`, token CSRF de `GET /api/v1/auth/csrf` en la cabecera `X-XSRF-TOKEN` de cada POST/PUT/PATCH/DELETE, y los errores Problem Details se convierten en `ApiError` (con `fieldErrors` e `issues`).
- En desarrollo, Vite hace de proxy de `/api` hacia `localhost:8080`: mismo origen, sin CORS. No añadir CORS al backend para el frontend.
- Nunca guardar tokens ni datos de sesión en `localStorage`: la sesión es la cookie HttpOnly.
- Nunca usar `dangerouslySetInnerHTML` con datos de usuario (IDs, nombres).

## Reproducción de ejecuciones

- La animación dibuja **muestras ya calculadas** (`/runs/{id}/samples`); nunca calcula tráfico (sección 2.3).
- Se dibuja en un `<canvas>` sobre React Flow, sincronizado con `useViewport`, a través de la interfaz `RunRenderer`. La futura vista 3D será otra implementación de la misma interfaz.

## Pruebas

- `npm test` (Vitest). Cada función pura nueva lleva pruebas; los componentes con lógica de interacción, una prueba con Testing Library.
- `npm run typecheck` sin errores.

## Al terminar

- [ ] `npm test` y `npm run typecheck` en verde.
- [ ] `docs/frontend.md` actualizado; limitaciones nuevas en `docs/limitaciones.md`.
