# Limitaciones conocidas

Cada entrada indica el impacto, la causa y cómo se resolvería. Al resolver una, se marca como resuelta con la
fecha; no se borra.

## Motor de simulación

### M1. No hay giros permisivos (ceder el paso)
- **Estado:** abierta, pospuesta hasta tener el editor (decisión del 2026-10-18).
- **Qué pasa:** dos movimientos que se cruzan nunca pueden tener verde en la misma fase. El clásico "verde
  para seguir recto y para girar a la izquierda cediendo el paso al tráfico de frente" se rechaza con
  `SIGNAL_CONFLICTING_MOVEMENTS`.
- **Impacto:** los planes semafóricos deben usar izquierdas protegidas (fase propia) o prohibir la izquierda.
  Es más estricto que muchos cruces reales, aunque coherente con la sección 8.3 ("los movimientos
  incompatibles no deben recibir autorización simultánea").
- **Solución propuesta:**
  1. Marcar un movimiento de una fase como *permisivo*.
  2. En cada tick, un vehículo permisivo solo avanza si ningún movimiento protegido con el que choca descarga
     vehículos en ese tick.
  3. Pruebas: con mucho tráfico de frente la izquierda casi no pasa; sin tráfico pasa normal.
  4. Subir la versión del motor a 0.3.0.

### M2. Conflictos calculados por geometría simplificada
- **Estado:** abierta, aceptada para el MVP.
- **Qué pasa:** el conflicto se calcula con los ángulos hacia los nodos vecinos (modelo de cuerdas). Se supone
  circulación por la derecha y eje Y hacia arriba.
- **Impacto:** las intersecciones con calles casi paralelas, o con nodos vecinos en la misma dirección, pueden
  clasificarse mal. Si el editor no invierte el eje Y de pantalla, se calcularía como circulación por la
  izquierda.
- **Solución:** el editor debe invertir Y. A futuro: plantillas de intersección (en T, en cruz) con conflictos
  predefinidos.

### M3. Rutas fijas a flujo libre
- **Estado:** abierta, aceptada para el MVP (sección 8.1).
- **Qué pasa:** cada demanda usa una única ruta, la más rápida sin tráfico, calculada al inicio. Los vehículos
  no evitan las calles congestionadas.
- **Solución futura:** recalcular el costo con el tiempo de espera estimado cada cierto intervalo.

### M4. Validación de conectividad aproximada
- **Estado:** abierta, menor.
- **Qué pasa:** `NetworkValidator` comprueba la alcanzabilidad ignorando las restricciones de giro. Las
  demandas sí se comprueban con las restricciones (`DEMAND_NO_ROUTE`).
- **Impacto:** una salida puede no recibir la advertencia `EXIT_UNREACHABLE` aunque los giros la vuelvan
  inalcanzable.

### M5. Espacio libre medido al inicio del tick
- **Estado:** abierta, decisión de diseño.
- **Qué pasa:** el espacio que libera un vehículo al salir de una calle no se reutiliza en el mismo tick. Es
  un criterio conservador que evita depender del orden de procesamiento.
- **Impacto:** reduce levemente la capacidad efectiva en colas muy densas. Con `dt = 0,5 s` el efecto es
  pequeño.

## Backend

### B1. El registro revela si un email ya existe
- **Estado:** abierta, aceptada para el MVP.
- **Qué pasa:** registrar un email existente responde 409. Un atacante podría averiguar qué emails están
  registrados. El login sí responde igual en ambos casos.
- **Solución futura:** verificación por correo ("si el email es válido, te llegará un enlace").

### B2. Sin límite de intentos de login
- **Estado:** ✅ resuelta el 2026-10-18 (`LoginThrottle`: por cuenta, por IP y registros por IP, con 429 y
  `Retry-After`).

### B3. El token CSRF no se renueva al iniciar sesión
- **Estado:** ✅ resuelta el 2026-10-18 (el login borra el token; se emite uno nuevo).

### B4. Sesiones en memoria
- **Estado:** ✅ resuelta el 2026-10-18 (Spring Session JDBC en PostgreSQL).

### B10. Límite de intentos en memoria de cada instancia
- **Estado:** abierta, aceptada para el piloto (una instancia).
- **Qué pasa:** `LoginThrottle` cuenta los intentos en la memoria del proceso. Con varias instancias detrás de
  un balanceador, cada una lleva su propia cuenta, y reiniciar borra los contadores.
- **Solución futura:** guardar los contadores en PostgreSQL o Redis (por ejemplo, con Bucket4j).

### B11. HTTPS fuera del stack
- **Estado:** abierta, por diseño.
- **Qué pasa:** `compose.yaml` sirve por HTTP en `127.0.0.1`. Para publicar en Internet hace falta un proxy con
  certificados (Caddy, documentado en `docs/despliegue.md`) y un dominio.

### B12. Sin análisis de vulnerabilidades de dependencias Java en el CI
- **Estado:** abierta, menor.
- **Qué pasa:** el frontend se audita con `npm audit` en cada build. Para Java hay Dependabot (alertas de
  GitHub), pero no un análisis en el propio CI (OWASP Dependency-Check necesita una clave de la API de NVD).
- **Solución:** activar las alertas de seguridad de Dependabot en GitHub, o añadir Dependency-Check con una
  clave de NVD como secreto del repositorio.

### B6. Ejecuciones atadas a una sola instancia del servidor
- **Estado:** abierta, aceptada para el MVP (sección 10.6: "en el MVP puede usarse un worker interno").
- **Qué pasa:** el pool de hilos y las señales de cancelación viven en la memoria del proceso. Al reiniciar el
  servidor, las ejecuciones en curso se pierden y se marcan `FAILED/INTERRUPTED`. Con varias instancias, la
  limpieza al arrancar de una marcaría como fallidas las ejecuciones de las otras.
- **Solución futura:** una cola de trabajos persistente (tabla con `SELECT ... FOR UPDATE SKIP LOCKED`) o un
  worker separado.

### B7. Límite de ejecuciones simultáneas con una pequeña carrera
- **Estado:** abierta, menor.
- **Qué pasa:** el límite se comprueba contando las ejecuciones activas antes de insertar. Dos peticiones
  exactamente simultáneas del mismo usuario podrían superarlo en una.
- **Impacto:** bajo; el pool global (2 hilos + cola de 20) sigue acotando la carga real.
- **Solución:** bloquear la fila del usuario (`SELECT ... FOR UPDATE`) durante la creación.

### B8. El límite de tamaño solo mira `Content-Length`
- **Estado:** abierta, menor.
- **Qué pasa:** `RequestSizeLimitFilter` rechaza con 413 según la cabecera `Content-Length`. Una petición con
  *chunked transfer encoding* no la trae y se lee igualmente. Los límites de Bean Validation (2000 calles,
  etc.) siguen acotando lo que se procesa.
- **Solución:** envolver el `InputStream` contando bytes, o configurar el límite en el proxy inverso.

### B9. Cancelación en curso y timeout sin prueba de integración
- **Estado:** abierta, menor.
- **Qué pasa:** el mecanismo (`RunControl`) está probado en el motor, y la cancelación de una ejecución
  pendiente está probada en la API. Pero cancelar una ejecución **en curso** y el timeout no tienen prueba de
  extremo a extremo, porque dependen del tiempo real y serían inestables.
- **Solución:** inyectar en las pruebas un control determinista (por ejemplo, un `RunControl` que se detenga
  en un tick concreto).

## Frontend

### F1. Semáforos solo con la plantilla "una fase por acceso"
- **Estado:** abierta.
- **Qué pasa:** el editor genera los planes con `splitPhasing` (verde y todo-rojo configurables). La API y el
  motor admiten planes libres (varias calles en verde en la misma fase, desfases), pero todavía no hay
  interfaz para editarlos.
- **Impacto:** no se pueden probar planes de dos fases (norte-sur y este-oeste), que son más eficientes.
- **Solución:** editor de fases con validación de conflictos en vivo. Depende también de la limitación M1.

### F2. Sin interfaz para el perfil de demanda por periodos
- **Estado:** abierta.
- **Qué pasa:** el motor y la API admiten `profile` (por ejemplo, hora punta), pero el panel de demanda solo
  edita la tasa base. Si un escenario guardado tiene periodos, se conservan, pero no se ven.

### F3. Sin deshacer/rehacer
- **Estado:** abierta. Por la arquitectura (definición inmutable en el store), es sencillo de añadir: basta
  con una pila de definiciones anteriores.

### F4. Animación por saltos de 5 s
- **Estado:** abierta, aceptada.
- **Qué pasa:** la animación muestra la muestra más cercana sin interpolar. Con el intervalo por defecto
  (5 s), los vehículos "saltan" en lugar de deslizarse. Las posiciones son una representación del recuento,
  no posiciones calculadas (modelo macroscópico, sección 6.3).
- **Solución:** interpolar entre muestras o pedir muestras más frecuentes en ejecuciones cortas.

### F5. Comparación de ejecuciones no disponible en la interfaz
- **Estado:** ✅ resuelta el 2026-10-18 (Fase 5, comparador de dos ejecuciones).

## Comparación

### C1. Una sola semilla por lado
- **Estado:** ✅ resuelta el 2026-10-18: lotes de 2 a 10 semillas, comparación pareada o de Welch e intervalo de
  confianza del 95 %. Un lote cuenta como una sola tarea.

### C4. Sin corrección por comparaciones múltiples
- **Estado:** abierta, aceptada.
- **Qué pasa:** se calcula un intervalo del 95 % para cada una de las ~8 métricas por separado. Mirando muchas
  métricas a la vez, la probabilidad de que alguna salga "significativa" solo por azar es mayor del 5 %. Además,
  las métricas están correlacionadas (tiempo de viaje y espera suben juntos).
- **Mitigación:** el usuario debería decidir antes qué métrica le importa (por ejemplo, el tiempo medio de
  viaje). Opción futura: corrección de Bonferroni o Holm, o elegir una "métrica principal" del experimento.

### C5. Normalidad supuesta con pocas semillas
- **Estado:** abierta, aceptada.
- **Qué pasa:** el intervalo t supone que las diferencias entre semillas se distribuyen aproximadamente normal.
  Con 2 o 3 semillas, el intervalo es muy ancho (correctamente conservador), pero si la distribución es muy
  asimétrica puede ser inexacto. Recomendado: 5 semillas o más.

### C2. Sin animación lado a lado
- **Estado:** abierta, menor.
- **Qué pasa:** el comparador muestra números y gráficos, pero no las dos animaciones sincronizadas.

### C3. Las métricas de calles eliminadas o nuevas no se comparan
- **Estado:** abierta, por diseño.
- **Qué pasa:** solo se comparan las calles presentes en ambos escenarios (mismo ID). Si B añade una calle, sus
  métricas no aparecen en la tabla de calles; el cambio sí aparece en "Qué cambió".

### B5. Las pruebas de la app necesitan Docker
- **Estado:** abierta, decisión de diseño.
- **Qué pasa:** las pruebas de integración usan un PostgreSQL real con Testcontainers.
- **Motivo:** probar contra el mismo motor de base de datos que producción, con las mismas migraciones.
