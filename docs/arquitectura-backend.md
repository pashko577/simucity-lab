# Arquitectura del backend (Spring Boot)

**Módulo:** `backend/app` · **Paquete:** `lab.simucity.app`

## Stack

| Pieza | Versión |
|---|---|
| Java | 21 (se compila con `--release 21`) |
| Spring Boot | 4.1.1 (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3) |
| PostgreSQL | 17 |
| Flyway | 12 (lo gestiona Spring Boot) |
| Pruebas | JUnit 6, MockMvc, Testcontainers 2 con PostgreSQL real |

## Estructura Maven

```
backend/
├── pom.xml       Padre: importa el BOM de Spring Boot (solo versiones) y declara los módulos
├── engine/       Motor: Java puro, sin dependencias de Spring
└── app/          Spring Boot: depende de engine, nunca al revés
```

El padre no hereda de `spring-boot-starter-parent`. Por eso `app/pom.xml` declara explícitamente el paso
`repackage` del plugin de Spring Boot para generar el jar ejecutable.

## Monolito modular

Un solo despliegue, con límites internos claros por paquete (sección 10.7 de la especificación).

| Paquete | Estado | Responsabilidad |
|---|---|---|
| `identity` | ✅ | Usuarios, registro, login, sesión, configuración de seguridad |
| `projects` | ✅ | CRUD de proyectos del usuario |
| `shared` | ✅ | Errores (Problem Details), paginación, límite de tamaño de las peticiones |
| `simulation` | ✅ | Integración del motor en Spring: límites del servidor y pool de hilos |
| `scenarios` | ✅ | Red, demanda y semáforos (JSONB); duplicar; validar con el motor |
| `runs` | ✅ | Ejecuciones asíncronas, cancelación, métricas, muestras y exportación CSV |
| `comparison` | ✅ | Comparar dos ejecuciones: condiciones justas, diferencias de escenario y de métricas |
| `audit` | ⏳ | Registro de eventos de seguridad (Fase 6) |

Reglas entre módulos:
- Un módulo guarda referencias a otros **por ID** (por ejemplo `Project.ownerId`), no por entidad JPA.
- Cadena de autorización: `ProjectService.getOwned` → `ScenarioService.getOwned` → ejecuciones filtradas por
  `owner_id`. Ningún módulo busca un recurso sin pasar por esta cadena.

## Escenarios

El editor envía la definición completa en JSON (`ScenarioDefinition`, versión de esquema 1):

```json
{
  "nodes":   [{"id": "E", "type": "ENTRY", "x": 0, "y": 0}],
  "edges":   [{"id": "e1", "from": "E", "to": "I", "lengthMeters": 100, "lanes": 1,
               "speedLimitMps": 10, "storageCapacity": 10, "closed": false}],
  "demands": [{"id": "d1", "origin": "E", "destination": "X", "vehiclesPerMinute": 12,
               "mode": "POISSON", "profile": [{"fromSeconds": 0, "toSeconds": 1800, "vehiclesPerMinute": 30}]}],
  "signals": [{"nodeId": "I", "offsetSeconds": 0, "phases": [
               {"durationSeconds": 30, "greenMovements": [{"from": "e1", "to": "e2"}]}]}],
  "prohibitedMovements": [{"from": "e1", "to": "e3"}]
}
```

- Se guarda como un único `JSONB` (`scenarios.definition`) junto a `schema_version`. La especificación
  proponía columnas separadas para red, demanda y semáforos; un solo documento es más simple y se copia entero
  como snapshot en cada ejecución.
- **Dos niveles de validación:** Bean Validation revisa la estructura (campos presentes, tamaños máximos: 500
  nodos, 2000 calles, 200 demandas). El motor revisa las reglas del dominio y responde con sus códigos
  (`EDGE_INVALID_LENGTH`, `SIGNAL_CONFLICTING_MOVEMENTS`...).
- `POST /scenarios/{id}/validate` siempre responde 200 con `{valid, issues}`, porque el editor la usa para
  mostrar los problemas mientras el usuario dibuja.

## Ejecuciones

```
POST /scenarios/{id}/runs ──► valida con el motor ──► guarda PENDING + snapshot ──► 202 Accepted
                                     │ errores → 400 con "issues"                       │
                                     ▼                                                   ▼ (tras el commit)
                               (nada se guarda)                         pool "simulation-" (2 hilos, cola 20)
                                                                                         │
                         PENDING ──► RUNNING ──► COMPLETED (métricas + muestras + resumen)
                            │           ├──► FAILED (TIMEOUT / ENGINE_ERROR / QUEUE_FULL / INTERRUPTED)
                            └───────────┴──► CANCELLED
```

- **Nunca** se ejecuta el motor dentro de la petición HTTP. El envío al pool ocurre después del commit
  (`@TransactionalEventListener`), para que el worker siempre encuentre el registro.
- **Snapshot inmutable:** cada ejecución guarda una copia del escenario, los parámetros completos, la semilla y
  la versión del motor. El worker trabaja **solo** con esa copia: editar el escenario después no altera nada
  (lo comprueba una prueba).
- **Sin semilla** en la petición, el servidor elige una y la registra, así la ejecución se puede repetir.
- **Cambios de estado atómicos:** `UPDATE ... WHERE status = 'PENDING'`. Si el worker y una cancelación
  compiten, solo uno gana, sin bloqueos.
- **Cancelación:** si está pendiente, es inmediata. Si está en curso, se pide al motor que se detenga
  (`RunControl`, revisado cada 256 ticks) y queda en `CANCELLED`.
- **Presupuesto de tiempo:** si el cálculo supera `simucity.runs.timeout` (120 s), se detiene y queda en
  `FAILED/TIMEOUT`.
- **Reinicio del servidor:** al arrancar, las ejecuciones que quedaron en `PENDING` o `RUNNING` se marcan
  `FAILED/INTERRUPTED`.
- **Errores:** el usuario ve un código y un mensaje propio, nunca el texto de la excepción. El detalle técnico
  queda solo en el log del servidor.

### Límites (`simucity.runs.*`)

| Propiedad | Valor | Para qué |
|---|---|---|
| `worker-threads` | 2 | Simulaciones en paralelo en el servidor |
| `queue-capacity` | 20 | Ejecuciones en espera; más allá se rechazan (`QUEUE_FULL`) |
| `max-active-per-user` | 2 | Ejecuciones `PENDING` + `RUNNING` por usuario; la siguiente recibe **429** |
| `max-duration-seconds` | 7200 | Duración simulada máxima (el motor admite 21 600) |
| `timeout` | 120 s | Tiempo real máximo de cálculo |
| `max-expected-vehicles` | 50 000 | Vehículos esperados según la demanda |
| `max-sample-cells` | 300 000 | Muestras × calles (tamaño de los datos de animación) |

Además, `simucity.api.max-request-bytes` (2 MB) rechaza con **413** los cuerpos demasiado grandes, antes de
leerlos.

### Resultados
- **Métricas:** una fila por métrica en `simulation_metrics`, insertadas en lote con JDBC, en el orden del
  motor.
- **Muestras para la animación:** un documento JSONB compacto por ejecución:
  `{"edgeIds": [...], "samples": [{"t": 5.0, "vehicles": [3, 0], "queuedByEdge": [1, 0], ...}]}`. Los arreglos
  siguen el orden de `edgeIds`; no hay una fila por calle y tick (sección 15.1).
- **CSV:** cada fila lleva `run_id`, `engine_version` y `seed`. Los textos que empiezan por `= + - @` se
  prefijan con `'` para evitar la **inyección de fórmulas** en Excel o LibreOffice (los IDs de calles los
  escribe el usuario).

## Seguridad

### Autenticación
- **Sesión con cookie** `SESSION` (Spring Session JDBC): `HttpOnly`, `SameSite=Lax`, `Secure` en producción y
  30 minutos de inactividad. Las sesiones se guardan en PostgreSQL (tablas `SPRING_SESSION*`, migración V4):
  sobreviven a reinicios y permiten varias instancias.
- **Límite de intentos** (`LoginThrottle`): 5 fallos en 15 min bloquean la cuenta, 20 la IP, y 10 registros por
  hora por IP. Responde 429 con `Retry-After`. Se comprueba antes de verificar la contraseña y se aplica exista
  o no la cuenta. La ventana es deslizante y en memoria; las claves antiguas se purgan cada 10 min.
- **Auditoría** (`audit.AuditLog`, tabla `audit_events`): registro, login (éxito, fallo, bloqueo), logout y
  borrado de proyectos y escenarios, con el ID del usuario y la IP. Nunca se guarda el email tecleado ni la
  contraseña. Retención de 180 días (`simucity.audit.retention`), con purga diaria.
- Contraseñas con **BCrypt** mediante `DelegatingPasswordEncoder` (el prefijo `{bcrypt}` permite migrar a
  Argon2 más adelante sin invalidar cuentas). Longitud de 12 a 128 caracteres y máximo 72 bytes, el límite de
  BCrypt: se rechaza en lugar de truncar en silencio.
- Emails normalizados a minúsculas, también garantizado con una restricción en la base de datos.
- Login fallido: siempre "Credenciales inválidas.", exista o no el email.
- **Fijación de sesión:** el ID de sesión cambia al iniciar sesión.
- La sesión guarda el usuario **sin** el hash de la contraseña.

### CSRF
- Patrón SPA de Spring Security (`csrf.spa()`): cookie `XSRF-TOKEN` legible por el frontend, cuyo **valor**
  este copia en la cabecera `X-XSRF-TOKEN` en cada `POST`, `PUT`, `PATCH` o `DELETE`.
- `GET /api/v1/auth/csrf` garantiza que la cookie exista y responde `{headerName, cookieName}`. **No**
  devuelve el token del cuerpo de la petición: esa es la versión enmascarada (BREACH), que `csrf.spa()`
  rechaza en la cabecera. Así estaba al principio, y lo detectó la prueba E2E del navegador.
- `/error` es público. Sin eso, un 403 (por ejemplo, de CSRF) se reenviaba a `/error`, que exigía sesión, y
  el cliente recibía un **401 engañoso** sin cuerpo.
- **El token se renueva al iniciar sesión** (B3): el login borra la cookie `XSRF-TOKEN` y `csrf.spa()` emite una
  nueva en la siguiente petición. Un token capturado antes del login deja de valer.
- Cabeceras de la API: `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` (solo devuelve
  JSON), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` y
  `Cache-Control: no-store`.
- `CsrfRealHttpTest` lo prueba con servidor y cliente HTTP reales, porque MockMvc sustituye el mecanismo
  CSRF y no puede detectar estos errores.

### Autorización (IDOR/BOLA)
- Denegar por defecto: solo son públicos el registro, el login, el token CSRF y `/actuator/health`.
- El ID del usuario sale **siempre** de la sesión, nunca del cuerpo ni de la URL.
- Los repositorios solo ofrecen consultas filtradas por dueño (`findByIdAndOwnerId`), así que es imposible
  olvidar la comprobación.
- Un recurso ajeno responde **404**, igual que uno inexistente, para no revelar que existe.
- Los IDs son UUID aleatorios.

### Errores
- Formato RFC 9457 (Problem Details), con mensajes en español.
- Nunca se devuelven trazas, nombres de excepciones ni mensajes internos. Los errores inesperados se registran
  en el servidor y el cliente recibe un mensaje genérico.
- Los errores de validación incluyen qué campo falló: `{"errors": {"name": "..."}}`.

### Otras medidas
- La paginación está limitada a 100 elementos (`simucity.api.max-page-size`).
- Actuator solo expone `health`, sin detalles.
- Las credenciales de la base de datos llegan por variables de entorno (`SIMUCITY_DB_URL`, `SIMUCITY_DB_USER`,
  `SIMUCITY_DB_PASSWORD`). Las de `application-dev.yml` son solo para desarrollo local.

## Comparación (Fase 5)

`POST /api/v1/comparisons {baseRunId, candidateRunId}` compara dos ejecuciones terminadas del usuario. Se calcula
al vuelo a partir de los snapshots y métricas guardados; no se persiste nada.

| Parte | Clase (pura, sin Spring) | Qué hace |
|---|---|---|
| Condiciones justas (sección 9.2) | `FairnessCheck` | Compara versión del motor, semilla, demanda, duración, paso temporal y flujo de saturación. `fair = true` solo si todo coincide; si no, lista qué difiere |
| Qué cambió (sección 5.3) | `ScenarioDiff` | Diferencias entre los dos snapshots por ID: nodos, calles (campo a campo), demandas, semáforos y giros prohibidos. Las posiciones de los nodos se ignoran (solo son visuales) |
| Variación de métricas (sección 9.3) | `MetricComparator` | Para cada métrica presente en ambas: diferencia absoluta, porcentaje `(B − A) / A × 100` (`null` si A = 0) y evaluación según `MetricNames.direction` del motor |

Evaluaciones: `IMPROVED`, `WORSENED`, `UNCHANGED`, `NEUTRAL` (métrica descriptiva, como los vehículos generados)
y `NOT_COMPARABLE` (falta el valor, como un percentil 95 sin observaciones suficientes).

`GET /api/v1/projects/{id}/runs` lista las ejecuciones de todo el proyecto con el nombre de su escenario (por
defecto solo las `COMPLETED`), para elegir qué comparar.

### Lotes de varias semillas (sección 9.2)

Con llegadas aleatorias, una sola ejecución por lado no permite distinguir una mejora real del azar. Un **lote**
es el mismo escenario ejecutado con N semillas consecutivas (`seed`, `seed + 1`… de 2 a 10).

- `POST /scenarios/{id}/batches {size, durationSeconds, seed?}`: se valida una vez y se crean N ejecuciones
  normales con `batch_id`. **Un lote cuenta como una sola tarea** en el límite de 2 por usuario
  (`count(distinct coalesce(batch_id, id))`), y sus ejecuciones se calculan **una tras otra** en el mismo hilo
  del pool, así que un lote no acapara el servidor.
- Estado derivado de sus ejecuciones: `PENDING`, `RUNNING`, `COMPLETED`, `PARTIAL` (alguna falló o se canceló;
  las estadísticas usan solo las completadas), `FAILED` o `CANCELLED`.
- `GET /batches/{id}` devuelve, por métrica de red, `n`, media, desviación típica, mínimo y máximo.

`POST /comparisons/batches {baseBatchId, candidateBatchId}` (`BatchComparator`, puro):
- **Mismas semillas en ambos lotes → comparación pareada:** se analiza la diferencia de cada par (semilla 42
  contra 42…). El azar de las llegadas es el mismo en ambos lados y se cancela, así que se detectan diferencias
  más pequeñas.
- **Semillas distintas → Welch** (diferencia de medias con varianzas distintas). La condición `SAME_SEEDS` lo
  advierte.
- Intervalo de confianza del 95 % con la t de Student (`engine/metrics/Statistics`, tabla de valores críticos;
  los grados de libertad fraccionarios de Welch se redondean hacia abajo, criterio conservador).
- **Solo se declara `IMPROVED` o `WORSENED` si el intervalo excluye el cero**; si lo incluye, `INCONCLUSIVE`.
- Dos experimentos de control en las pruebas: el mismo escenario con las mismas semillas da todo `UNCHANGED`, y
  con semillas distintas el tiempo de viaje sale `INCONCLUSIVE` (no inventa diferencias).

## Base de datos

- El esquema lo controla **Flyway** (`app/src/main/resources/db/migration`). Hibernate solo valida
  (`ddl-auto: validate`).
- Las migraciones aplicadas **no se editan nunca**: cada cambio es un archivo `V{n}__descripcion.sql` nuevo.
- `open-in-view: false`: no hay consultas perezosas fuera de las transacciones de servicio.
- Las fechas se guardan como `TIMESTAMPTZ` en UTC. El `Clock` de la aplicación avanza en microsegundos, la
  misma precisión que PostgreSQL.

| Migración | Contenido |
|---|---|
| `V1__users_and_projects.sql` | `users` (email único en minúsculas, estado) y `projects` (dueño, nombre, descripción, visibilidad, fechas) |
| `V4__sessions_and_audit.sql` | `SPRING_SESSION` y `SPRING_SESSION_ATTRIBUTES` (esquema oficial de Spring Session 4.1.1) y `audit_events` |
| `V3__run_batches.sql` | `run_batches` (escenario, dueño, tamaño 2..10, semilla base, duración) y `simulation_runs.batch_id` |
| `V2__scenarios_and_runs.sql` | `scenarios` (definición JSONB), `simulation_runs` (estado, semilla, versión del motor, snapshots JSONB, resumen, error), `simulation_metrics`, `simulation_samples` (JSONB compacto) |

Las columnas JSONB se mapean en JPA como `String` con `@JdbcTypeCode(SqlTypes.JSON)`; la conversión a objetos la
hace la aplicación con Jackson 3 (`ScenarioCodec`). Así no se depende de que Hibernate tenga un conversor JSON
configurado.

## API (v1)

| Método | Ruta | Auth | Respuesta |
|---|---|---|---|
| `GET` | `/api/v1/auth/csrf` | pública | `{headerName, cookieName}` + cookie `XSRF-TOKEN` |
| `POST` | `/api/v1/auth/register` | pública | 201 `{id, email}` · 400 · 409 si el email existe |
| `POST` | `/api/v1/auth/login` | pública | 200 `{id, email}` + cookie de sesión · 401 |
| `POST` | `/api/v1/auth/logout` | sesión | 204 |
| `GET` | `/api/v1/auth/me` | sesión | 200 `{id, email}` |
| `POST` | `/api/v1/projects` | sesión | 201 + `Location` |
| `GET` | `/api/v1/projects?page=&size=` | sesión | Página de proyectos propios, los más recientes primero |
| `GET` | `/api/v1/projects/{id}` | sesión | 200 · 404 si no existe o es ajeno |
| `PATCH` | `/api/v1/projects/{id}` | sesión | Actualización parcial (`name`, `description`) |
| `DELETE` | `/api/v1/projects/{id}` | sesión | 204 |
| `POST` | `/api/v1/projects/{projectId}/scenarios` | sesión | 201; sin `definition` empieza vacío |
| `GET` | `/api/v1/projects/{projectId}/scenarios` | sesión | Página de resúmenes (sin la definición) |
| `GET` | `/api/v1/scenarios/{id}` | sesión | Escenario con su definición |
| `PUT` | `/api/v1/scenarios/{id}` | sesión | Reemplaza el nombre y la definición |
| `POST` | `/api/v1/scenarios/{id}/duplicate` | sesión | 201; nombre opcional (por defecto "… (copia)") |
| `DELETE` | `/api/v1/scenarios/{id}` | sesión | 204 (borra también sus ejecuciones) |
| `POST` | `/api/v1/scenarios/{id}/validate` | sesión | 200 `{valid, issues}`; cuerpo opcional con parámetros |
| `POST` | `/api/v1/scenarios/{id}/runs` | sesión | **202** `PENDING` · 400 con `issues` · 429 si se supera el límite |
| `GET` | `/api/v1/scenarios/{id}/runs` | sesión | Página de ejecuciones, las más recientes primero |
| `GET` | `/api/v1/runs/{id}` | sesión | Estado, parámetros y, si terminó, métricas de red, anomalías y advertencias |
| `POST` | `/api/v1/runs/{id}/cancel` | sesión | Estado actualizado · 409 si ya terminó |
| `GET` | `/api/v1/runs/{id}/metrics?scope=` | sesión | Todas las métricas (filtro opcional `NETWORK`, `SEGMENT`, `ENTRY`) · 409 si no terminó |
| `GET` | `/api/v1/runs/{id}/samples` | sesión | Muestras compactas para la animación |
| `GET` | `/api/v1/runs/{id}/export.csv` | sesión | Descarga CSV de las métricas |
| `GET` | `/api/v1/projects/{projectId}/runs?status=` | sesión | Ejecuciones del proyecto con nombre de escenario (por defecto `COMPLETED`) |
| `POST` | `/api/v1/comparisons` | sesión | `{baseRunId, candidateRunId}` → comparación · 400 si son la misma · 404 si alguna es ajena · 409 si no terminó |
| `POST` | `/api/v1/scenarios/{id}/batches` | sesión | **202** lote de N semillas (`size` 2..10) · 400 · 429 |
| `GET` | `/api/v1/batches/{id}` | sesión | Estado, ejecuciones por semilla y resumen estadístico por métrica |
| `POST` | `/api/v1/batches/{id}/cancel` | sesión | Cancela las pendientes y detiene la que está en curso · 409 si ya terminó |
| `GET` | `/api/v1/projects/{projectId}/batches` | sesión | Lotes del proyecto con nombre de escenario |
| `POST` | `/api/v1/comparisons/batches` | sesión | `{baseBatchId, candidateBatchId}` → comparación estadística · 409 si algún lote tiene menos de 2 ejecuciones completadas |

Formato de página: `{content, page, size, totalElements, totalPages}`.

Cuerpo de `POST /runs`: `{"durationSeconds": 3600}` es obligatorio. Opcionales: `seed`, `dtSeconds`,
`sampleIntervalSeconds`, `saturationFlowPerLaneVph` y `stallThresholdSeconds`; los que faltan toman los valores
por defecto del motor.

## Pruebas

80 pruebas en `app/src/test`: 56 de integración y 24 unitarias.

Las pruebas con MockMvc (`ApiTestSupport`) usan la sesión del contenedor, porque MockMvc transporta la sesión
como objeto y no como cookie, y elevan los límites por IP, ya que todas comparten `127.0.0.1`. Las sesiones de
Spring Session, el protocolo CSRF real y las cabeceras se prueban con HTTP real en `RealHttpSecurityTest`. Levantan la aplicación completa y un **PostgreSQL real** con
Testcontainers, así que requieren Docker en ejecución.

| Prueba | Qué garantiza |
|---|---|
| `RealHttpSecurityTest` (4) | HTTP real con Spring Session: protocolo CSRF; un token incorrecto da 403 (no 401); sesión guardada en PostgreSQL y borrada al cerrar sesión; cookie HttpOnly y SameSite; token CSRF renovado al iniciar sesión; cabeceras de seguridad |
| `LoginThrottleTest` (6) | Ventana deslizante con reloj controlado: bloqueo por cuenta y por IP, desbloqueo exacto, limpieza tras un login correcto, registros por IP, purga |
| `LoginThrottleApiTest` (4) | 5 fallos → 429 con `Retry-After` incluso con la contraseña correcta · otra cuenta no se ve afectada · un email inexistente se bloquea igual · auditoría completa sin email ni contraseña · retención |
| `AutoSampleIntervalTest` (3) | Intervalo de muestreo automático según el tamaño de la red y la duración |
| `AuthApiTest` (10) | Registro, login, me, logout · email duplicado sin importar mayúsculas · mismo mensaje con email inexistente o contraseña incorrecta · validación de contraseña · 403 sin CSRF · health sin detalles |
| `ProjectApiTest` (6) | Ciclo completo de un proyecto · **otro usuario no puede ver, editar ni borrar** · 401 sin sesión · validaciones · ID mal formado sin filtrar detalles · paginación limitada |
| `ScenarioApiTest` (7) | Crear, ver, reemplazar, borrar y duplicar · validar con el motor · estructura inválida → 400 · cuerpo > 2 MB → 413 · **IDOR en los 7 endpoints** |
| `BatchApiTest` (8) | Lote de 5 semillas consecutivas con resumen · ampliar la calle mejora con el intervalo por debajo de 0 · **controles:** mismas semillas → todo sin cambios; semillas distintas → no concluyente · tamaño inválido · un lote = una tarea · cancelación · **IDOR** |
| `BatchComparatorTest` (5) | Pareada frente a Welch · mejora significativa · no concluyente · sin cambios · no comparable y neutral |
| `ComparisonLogicTest` (10) | Variación y evaluación de métricas, base 0, valores ausentes · diferencias entre escenarios (campos, altas, bajas, semáforos, giros; las posiciones se ignoran) · condiciones justas |
| `ComparisonApiTest` (6) | **Experimento real:** calle saturada frente a calle ampliada con la misma semilla → comparación justa, mismos vehículos generados, viaje medio −75 % · semillas distintas → aviso · ejecuciones del proyecto · misma ejecución → 400 · **IDOR** (no se puede mezclar una ejecución propia con una ajena) |
| `RunApiTest` (10) | Ejecución asíncrona con 30 s de viaje exactos · **misma semilla = mismas métricas** · editar el escenario no altera ejecuciones anteriores · errores del motor → 400 · límite de duración · semilla automática registrada · 429 por ejecuciones simultáneas · cancelación · CSV con protección contra fórmulas · **IDOR en ejecuciones y descargas** |

Verificado además a mano contra la aplicación en ejecución (perfil `dev` + Docker):
- Cabecera `X-XSRF-TOKEN` y cookie `HttpOnly; SameSite=Lax`.
- Migraciones V1 y V2 aplicadas sobre una base ya existente.
- Una hora simulada de un cruce con semáforo: 1226 vehículos (se esperaban unos 1200). Viaje medio de 52,29 s
  = 34 s de recorrido libre + 18,29 s de espera.
