# Motor de simulación

**Módulo:** `backend/engine` · **Paquete:** `lab.simucity.engine` · **Versión:** `EngineVersion.CURRENT` = `0.2.0`

El motor es la fuente de verdad del producto (sección 2.3 de la especificación). Es Java puro: no depende
de Spring, de la base de datos ni del frontend, y no hace I/O. Se prueba con JUnit sin levantar nada.

## Uso

```java
Scenario scenario = new Scenario(network, demands, signalPlans, prohibitedMovements);
RunParameters params = RunParameters.defaults(/* semilla */ 42, /* duración s */ 1800);

SimulationResult result = new SimulationEngine().run(scenario, params);
// Lanza InvalidScenarioException (con la lista completa de errores) si el escenario no es válido.

// Con control de parada (cancelación o tiempo máximo). Se consulta cada 256 ticks; no altera resultados.
engine.run(scenario, params, () -> cancelado.get());   // lanza SimulationAbortedException al detenerse

// Solo validar, sin ejecutar
ValidationResult validation = engine.validate(scenario, params);
```

## Paquetes

| Paquete | Responsabilidad |
|---|---|
| `network` | Grafo dirigido inmutable (`Network`, `Node`, `Edge`), movimientos (`Movement`) y reglas de giro (`MovementRules`) |
| `routing` | `DijkstraRouter`: ruta de menor tiempo a flujo libre |
| `validation` | `NetworkValidator`, `ScenarioValidator`, `MovementConflicts`: errores y advertencias legibles |
| `simulation` | Configuración del escenario, ciclo de simulación (`SimulationRun`) y resultado |
| `metrics` | `Metric`, nombres estables (`MetricNames`) y `Comparison` |

## Modelo: colas por segmento

- La red es un **grafo dirigido**; los dos sentidos de una calle son aristas distintas.
- Cada calle es una **cola FIFO**. Un vehículo la recorre en `ceil(longitud / velocidad / dt)` ticks y queda
  "listo" para salir.
- Un vehículo listo sale si: (1) el semáforo autoriza su movimiento, (2) la calle tiene **capacidad de
  descarga** disponible, y (3) la calle siguiente tiene **espacio de almacenamiento**.
- Capacidad de descarga: cubo de fichas que se recarga al flujo de saturación (`carriles × 1800 veh/h` por
  defecto). Un vehículo solo pasa sin demora; una cola sale a ritmo de saturación.
- Si la calle siguiente está llena, el vehículo espera: **las colas se propagan** hacia atrás.
- Los vehículos que no caben en la primera calle esperan en una **cola de entrada**.

Unidades internas del sistema internacional: metros, segundos, m/s.

**Carriles frente a espacio:** el flujo máximo de una calle lo limita tanto la descarga (`carriles × saturación`)
como el almacenamiento (`capacidad / tiempo de recorrido`). Duplicar los carriles sin duplicar la capacidad
apenas cambia el resultado: una calle de 200 m con 10 vehículos de capacidad sigue limitada a 10 veh / 20 s. Es
correcto según el modelo; por eso el editor escala la capacidad al cambiar los carriles (lo detectó la prueba
de comparación, que al principio mostraba solo −0,9 %).

## Ciclo de cada tick

| # | Fase | Qué hace |
|---|---|---|
| 1 | `updateSignals` | Fase actual de cada semáforo, calculada desde el número de tick |
| 2 | `generateArrivals` | Llegadas de cada demanda (periódicas o Poisson, con perfil por periodos) |
| 3 | `computeCandidateMoves` | Quién podría avanzar, según el estado **al inicio** del tick |
| 4 | `resolveCapacity` | Reparte el espacio libre de cada calle entre las colas que compiten (round-robin rotativo) |
| 5 | `applyMoves` | Aplica todos los movimientos aceptados a la vez |
| 6 | `updateMetrics` | Colas, esperas y detección de bloqueos |
| 7 | `sample` | Guarda una muestra del estado según la frecuencia configurada |

## Reproducibilidad

Misma red + mismos parámetros + misma semilla + misma versión del motor = **resultado idéntico**
(lo comprueba una prueba que compara el resultado completo).

- El tiempo se cuenta en ticks enteros; nunca se acumulan decimales.
- Cada demanda tiene su propio `java.util.Random` derivado de la semilla y de su ID. Agregar una demanda o
  cambiar un semáforo no altera las llegadas de las demás, así que los escenarios se comparan con los mismos
  vehículos.
- Se usa `StrictMath` y colecciones con orden estable: el resultado no depende del orden en que se crearon
  los elementos (también lo comprueba una prueba).
- Cambiar cualquier regla que altere resultados obliga a subir `EngineVersion.CURRENT`.

## Rutas y giros

- Ruta de menor tiempo a flujo libre (`longitud / velocidad`), calculada una vez al inicio.
- La búsqueda se hace **sobre aristas**: llegar a un nodo por calles distintas permite giros distintos.
- Por defecto se permite todo giro excepto el **giro en U**. Se pueden prohibir giros concretos con
  `Scenario.prohibitedMovements`.
- Si una demanda no tiene ruta, se emite la advertencia `DEMAND_NO_ROUTE` y no se generan sus vehículos.

## Semáforos

- Un plan es una lista de fases que se repite en ciclo, con desfase opcional. Una fase sin movimientos es
  todo-rojo (despeje).
- Una intersección sin plan no tiene semáforo: todos sus giros permitidos están autorizados.
- **Conflictos:** dos movimientos con verde en la misma fase no pueden cruzarse
  (`SIGNAL_CONFLICTING_MOVEMENTS`). Se calcula con la geometría de los nodos: cada movimiento es una cuerda en
  un círculo alrededor de la intersección, y dos cuerdas que se cortan son un conflicto. Si comparten la
  salida, convergen y también es conflicto.
- **Convención:** eje Y hacia arriba y circulación por la derecha. El editor debe invertir Y si usa
  coordenadas de pantalla.
- `SignalTemplates.splitPhasing` genera un plan sin conflictos: una fase por acceso más un todo-rojo.

## Demanda

- Tasa base en veh/min y modo de llegada `PERIODIC` o `POISSON`.
- Perfil opcional con `DemandPeriod(desde, hasta, tasa)`, por ejemplo para la hora punta. Los periodos no
  pueden solaparse.
- Las llegadas se derivan de la integral acumulada `expectedVehiclesUntil(t)`, que se calcula desde cero en
  cada tick.

## Resultado

`SimulationResult` contiene la versión del motor, los parámetros completos, las métricas, las muestras para
la animación, las anomalías y las advertencias de validación.

| Métrica | Alcance | Unidad | Definición |
|---|---|---|---|
| `trips_generated` / `trips_completed` / `trips_incomplete` | red | veh | Viajes creados, terminados y en curso al final |
| `travel_time_mean` | red | s | Desde que se genera el vehículo (incluye la espera en la entrada) hasta que sale |
| `travel_time_p95` | red | s | Percentil 95 por rango más cercano; no aplica con menos de 20 viajes |
| `wait_time_mean` | red | s | Tiempo en que el vehículo podía avanzar pero estaba detenido |
| `throughput` | red | veh/h | Viajes completados por hora |
| `queue_mean` | red / calle | veh | Promedio en el tiempo de vehículos en cola |
| `queue_max` | calle / entrada | veh | Cola máxima |
| `vehicles_discharged` | calle | veh | Vehículos que salieron de la calle |
| `utilization` | calle | ratio | Descargados / capacidad de descarga en la duración |

`MetricNames.direction(nombre)` indica cómo interpretar una variación al comparar: `LOWER_IS_BETTER` (tiempos,
colas, viajes sin terminar), `HIGHER_IS_BETTER` (viajes completados, flujo) o `NEUTRAL` (descriptivas, como los
vehículos generados).

Una métrica sin observaciones suficientes tiene valor `null` ("no aplica"). Cada métrica indica cuántas
observaciones se usaron.

**Anomalías:** `EDGE_STALLED` y `ENTRY_STALLED` aparecen cuando hay cola y **ningún** vehículo sale durante el
umbral (300 s por defecto). Una cola larga que avanza no es una anomalía: es un resultado del escenario.

## Validación

`ScenarioValidator` devuelve todos los problemas juntos, con un código estable y un mensaje en español:

- **Errores** (impiden ejecutar): parámetros fuera de rango, red inválida, demanda con origen o destino
  incorrecto, periodos solapados, semáforo con giros inexistentes, prohibidos o en conflicto, y límites
  excedidos (tamaño de red, vehículos esperados, número de muestras).
- **Advertencias:** red desconectada, salidas inalcanzables, demanda sin ruta.

## Pruebas

57 pruebas en `backend/engine/src/test`, entre ellas todas las mínimas de la sección 16.1 de la
especificación. Valores clave verificados a mano: 30 s de viaje en vía libre, 10,5 s de espera exacta ante un
semáforo en rojo, y que la capacidad nunca se supera en ningún tick.
