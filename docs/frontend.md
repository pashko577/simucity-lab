# Frontend (editor web)

**Carpeta:** `frontend/` · **Skill:** `.claude/skills/componente-frontend`

## Stack

| Pieza | Versión | Para qué |
|---|---|---|
| React | 19 | Interfaz |
| TypeScript | 7 | Tipos (`npm run typecheck`) |
| Vite | 8 | Servidor de desarrollo con proxy a la API, y build |
| React Flow (`@xyflow/react`) | 12 | Editor del grafo: nodos, calles, zoom, minimapa |
| Zustand | 5 | Estado del editor y de la sesión |
| React Router | 8 | Rutas |
| Vitest + Testing Library | 5 / 16 | Pruebas unitarias y de componentes |
| Playwright | 1.64 | Prueba de extremo a extremo en un navegador real |

## Pantallas

| Ruta | Pantalla |
|---|---|
| `/login` | Iniciar sesión o crear cuenta |
| `/projects` | Mis proyectos: crear, abrir, eliminar |
| `/projects/:id` | Escenarios del proyecto: crear, abrir, duplicar, eliminar |
| `/scenarios/:id` | **Editor**: lienzo, herramientas, propiedades, demanda, validación, ejecución y animación |
| `/projects/:id/compare?base=…&candidate=…` | **Comparador** de dos ejecuciones (A = base, B = alternativa) |

El editor se carga en diferido (`React.lazy`): React Flow solo se descarga al abrir un escenario.

## Editor

```
┌ Cabecera: ← Proyecto · nombre del escenario · Guardar ──────────────────────────────┐
├ Herramientas: Seleccionar · + Entrada · + Intersección · + Salida · Conectar · Eliminar ┤
├──────────────────────────────────────────────┬──────────────────────────────────────┤
│                                              │ Propiedades (nodo o calle)            │
│   Lienzo React Flow                          │ Demanda                               │
│   + capa <canvas> de animación               │ Validación                            │
│                                              │ Ejecutar simulación + métricas        │
├ Reproducción: ▶ · línea de tiempo · ×1/×5/×20/×60 · leyenda ─┤                      │
└──────────────────────────────────────────────┴──────────────────────────────────────┘
```

**Cómo se dibuja una red:**
1. Coloca entradas, intersecciones y salidas haciendo clic en el lienzo.
2. Con **Conectar calle**, haz clic en el origen y luego en el destino. Con "Doble sentido" se crean las dos
   calles; el destino pasa a ser el nuevo origen, así que se pueden encadenar.
3. Al seleccionar una calle, edita longitud (m), carriles, velocidad (**km/h**; se guarda en m/s), capacidad
   (veh) y si está cerrada, o crea su sentido contrario.
4. Al seleccionar una intersección, activa el **semáforo** (plantilla de una fase por acceso, con verde y
   todo-rojo configurables) y marca o desmarca los **giros permitidos**.
5. En **Demanda**, elige origen, destino, tasa (veh/min) y tipo de llegadas.
6. **Validar** guarda y consulta al motor. Los errores se listan; al hacer clic en uno se selecciona el
   elemento, que también se resalta en rojo en el lienzo.
7. **Ejecutar** guarda, lanza la ejecución, consulta el estado cada 0,7 s y al terminar muestra las métricas
   explicadas, el enlace al CSV y la animación.

Teclado: **Supr** borra la selección · **Esc** vuelve a Seleccionar · **Ctrl+S** guarda. El navegador avisa
si se cierra la pestaña con cambios sin guardar.

### Decisiones técnicas
- **La fuente de verdad es la `ScenarioDefinition` del store**, no React Flow. Los nodos y calles de React
  Flow se derivan del store. React Flow solo guarda su estado local (dimensiones, posición durante el
  arrastre) y la posición final se confirma al soltar el nodo.
- **Lógica pura y probada** en `editor/model.ts` (operaciones sobre la red, borrado en cascada, semáforos,
  unidades), `editor/geometry.ts` y `playback/samples.ts`. Los componentes solo llaman a esas funciones.
- **Eje Y:** el motor usa Y hacia arriba y la pantalla hacia abajo. Toda conversión pasa por
  `toFlowPosition` / `fromFlowPosition`. Así se cumple la convención que necesita la detección de conflictos
  semafóricos (limitación M2).
- **Calles de doble sentido:** cada sentido se dibuja desplazado hacia su derecha (`offsetToRight`) para que
  se vean como dos carriles.
- **Semáforo sin calles:** si se activa en una intersección todavía sin salidas, el plan se conserva vacío y
  se muestra un aviso. Al conectar calles, el plan se regenera solo, conservando los tiempos.
- **Encuadre:** el zoom automático solo se aplica al abrir un escenario con contenido. Con uno vacío, React
  Flow encuadraría al crear el primer nodo y los clics siguientes caerían en otro sitio (error encontrado con
  la prueba E2E).

## Comparador (Fase 5)

Se llega desde **"Comparar ejecuciones"** en la página del proyecto, o desde **"Comparar con otra ejecución"** en
el panel de ejecución del editor (con A ya elegida). La selección vive en la URL, así que se puede recargar o
compartir.

Flujo típico de un experimento: duplicar el escenario → cambiar una cosa → ejecutar ambos **con la misma
semilla** → comparar.

| Sección | Contenido |
|---|---|
| Condiciones | ✓ comparación justa, o ⚠ con la lista de lo que difiere (semilla, demanda, duración…) |
| Qué cambió de A a B | Frases legibles con unidades del editor: "Calle e1: carriles: 1 → 2; capacidad: 10 veh → 20 veh" |
| Métricas clave | Un pequeño gráfico de barras por métrica (tiempo de viaje, espera, cola, viajes completados) |
| Todas las métricas | Tabla con A, B, diferencia, variación % y evaluación |
| Calles más afectadas | Las 10 calles con mayor cambio de cola media, con cola máxima y utilización |

**Gráficos** (según la skill de visualización de datos):
- Un gráfico por métrica, nunca dos ejes: cada métrica tiene su unidad y su escala.
- A en azul y B en naranja. Ambos colores fueron validados con `validate_palette` contra las superficies clara y
  oscura de la app: pasan las pruebas de daltonismo, de visión normal y de contraste.
- La identidad nunca depende solo del color: cada barra lleva su rótulo "A" o "B" y hay leyenda.
- Barras de como máximo 24 px, extremo redondeado de 4 px y valor en la punta, en color de texto.
- Mejora y empeoramiento usan colores de **estado** reservados (verde y rojo), siempre con icono y texto
  ("▲ Mejora", "▼ Empeora"), nunca solo color. La tabla es la vista accesible de los mismos datos.

### Lotes de varias semillas

- **En el editor**, el campo **"Repeticiones (semillas)"** del panel de ejecución: con 1 es una ejecución normal;
  con 2 a 10 se lanza un lote con semillas consecutivas desde la "semilla inicial". Se ve el progreso
  (3/5 ejecuciones), luego la tabla de media ± desviación por métrica, y se anima la primera semilla.
- **En el comparador**, la pestaña **"Lotes (varias semillas)"** (`?mode=batches`):
  - El aviso indica si la comparación es **pareada** (mismas semillas) o de medias (Welch).
  - Los gráficos muestran la media como barra y ± la desviación como una línea fina con topes.
  - La tabla incluye media ± desviación de A y B, la diferencia media, el **IC 95 %** ("[−134; −86] s") y la
    evaluación. **"◆ No concluyente"** aparece cuando el intervalo incluye el cero.
- Los números se redondean **por magnitud** (−1,25 → −1,3, igual que 1,25 → 1,3). `Math.round` de JavaScript
  redondea hacia +∞ y deformaba los intervalos (lo detectó una prueba).

**Editor:** cambiar los carriles de una calle escala su capacidad en proporción (`setLanes`). Sin eso, "ampliar
una calle" apenas cambiaba el resultado, porque el espacio seguía limitando el flujo (ver `docs/motor.md`).

## Animación

```
/runs/{id}/samples ──► playback/samples.ts ──► RunRenderer ──► CanvasRenderer (2D)
   (muestras compactas)   (qué muestra mostrar,    (interfaz)       └► futura vista 3D (Three.js)
                           posición de vehículos)
```

- Solo **muestra** estados ya calculados por el motor. Pausar o cambiar la velocidad no altera ningún
  resultado (sección 2.3).
- Es un `<canvas>` encima de React Flow que sigue su zoom y desplazamiento (`useViewport`) y no captura el
  ratón.
- Cada calle se colorea por ocupación (vehículos / capacidad: verde → amarillo → rojo). Cada vehículo es un
  punto: los que están en cola se apilan al final de la calle y los demás se reparten en el tramo libre.
- **La vista 3D** será otra implementación de `RunRenderer` con los mismos datos; no requiere cambios en el
  motor ni en la API.

## Cliente de la API y seguridad

- `api/client.ts` centraliza todas las llamadas: `credentials: 'same-origin'` y conversión de Problem Details
  en `ApiError` (mensaje, `fieldErrors`, `issues`).
- **Sesión:** cookie HttpOnly gestionada por el navegador; no se guarda nada en `localStorage`.
- **CSRF:** en cada POST, PUT, PATCH o DELETE se copia el **valor de la cookie** `XSRF-TOKEN` en la cabecera
  `X-XSRF-TOKEN`. Si la cookie no existe, se pide a `GET /api/v1/auth/csrf`. Ante un 403, se renueva y se
  reintenta una vez.
- **Proxy de Vite:** `/api` → `http://localhost:8088` (o `SIMUCITY_API_URL`). Así el navegador ve un solo
  origen, y no hace falta configurar CORS en el backend.
- Ningún dato del usuario (IDs, nombres) se inserta como HTML.

## Ejecutar

```bash
# Backend en el puerto 8088 (perfil dev) + PostgreSQL en Docker: ver docs/README.md
cd frontend
npm install
npm run dev          # http://localhost:5173
npm test             # pruebas unitarias y de componentes (Vitest)
npm run typecheck
npm run e2e          # prueba E2E: levanta backend y Vite si no están en marcha (requiere Docker y el jar construido)
```

## Pruebas

| Archivo | Qué garantiza |
|---|---|
| `api/client.test.ts` (8) | Cabecera CSRF con el valor de la cookie · solo pide `/auth/csrf` si falta · reintento ante 403 · errores en español · 204 |
| `editor/model.test.ts` (13) | Inversión del eje Y · IDs · longitud y capacidad de calles nuevas · sentido contrario · borrado en cascada · plantilla semafórica sin giro en U · regeneración del plan · unidades |
| `editor/geometry.test.ts` (6) | Desplazamiento a la derecha según el sentido · recorte en los nodos |
| `editor/store.test.ts` (5) | Herramientas: crear nodos, conectar en uno o dos sentidos encadenando, borrar |
| `editor/PropertiesPanel.test.tsx` (4) | km/h ↔ m/s · valor inválido no se guarda · sentido contrario · semáforo sin calles |
| `playback/samples.test.ts` (7) | Elegir la muestra por tiempo · posición de vehículos en cola · colores · reloj |
| `comparison/format.test.ts` (8) | Porcentajes y diferencias en español con redondeo simétrico · media ± desviación e IC 95 % · evaluación con icono y texto (incluido "no concluyente") · descripción de cambios con km/h · calles ordenadas por impacto |
| `comparison/MetricBars.test.tsx` (2) | Rótulos A/B, valores, evaluación y descripción accesible · líneas de dispersión en lotes |
| `e2e/comparacion.spec.ts`: lotes (1) | **Navegador real:** lote de 5 semillas desde el editor con "Repeticiones" → comparar con otro lote → pareada, "Mejora" con intervalo negativo, vehículos generados sin cambios |
| `e2e/comparacion.spec.ts` (1) | **Navegador real:** calle saturada frente a calle ampliada → comparación justa, cambios descritos, "Mejora" en el tiempo de viaje; al intercambiar A y B, "Empeora" |
| `e2e/flujo-completo.spec.ts` (1) | **Navegador real:** crear cuenta → proyecto → escenario → dibujar un cruce → semáforo → demanda → validar → ejecutar → métricas y CSV → animación con línea de tiempo → recargar y comprobar que se guardó |

### Errores reales encontrados por las pruebas
- **CSRF (backend):** `/auth/csrf` devolvía el token enmascarado, que `csrf.spa()` rechaza en la cabecera.
  Además, el 403 se convertía en un 401 sin cuerpo porque `/error` exigía sesión. Las pruebas con MockMvc no
  lo detectaban (sustituyen el mecanismo CSRF); lo detectó la prueba E2E. Corregido, y cubierto ahora por
  `CsrfRealHttpTest` con HTTP real.
- **Encuadre del lienzo:** ver "Decisiones técnicas".
- **Semáforo que desaparecía** sin aviso en una intersección sin salidas.
