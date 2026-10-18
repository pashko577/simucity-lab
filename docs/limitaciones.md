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
- **Estado:** abierta. Se resuelve en la Fase 6.
- **Impacto:** permite intentar contraseñas por fuerza bruta. BCrypt lo hace lento, pero no lo impide.
- **Solución:** limitar la frecuencia por IP y por cuenta (sección 13.1, "limitar frecuencia de solicitudes").

### B3. El token CSRF no se renueva al iniciar sesión
- **Estado:** abierta, menor.
- **Qué pasa:** como el login es un endpoint propio (JSON) y no el formulario de Spring Security, no se aplica
  la renovación automática del token CSRF.
- **Solución:** renovar el token en `AuthController.login`.

### B4. Sesiones en memoria
- **Estado:** abierta, aceptada para el MVP.
- **Impacto:** reiniciar el servidor cierra todas las sesiones, y no se puede escalar a varias instancias.
- **Solución futura:** Spring Session con PostgreSQL o Redis.

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
- **Estado:** abierta. Es la Fase 5.

### B5. Las pruebas de la app necesitan Docker
- **Estado:** abierta, decisión de diseño.
- **Qué pasa:** las pruebas de integración usan un PostgreSQL real con Testcontainers.
- **Motivo:** probar contra el mismo motor de base de datos que producción, con las mismas migraciones.
