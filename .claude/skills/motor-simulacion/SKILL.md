---
name: motor-simulacion
description: Reglas y estructura para escribir, modificar o revisar el motor de simulación de tráfico de SimuCity Lab (backend/engine) - grafo dirigido, paso temporal, llegadas, colas, capacidad, semáforos, rutas Dijkstra/A*, semilla y métricas. Úsala siempre que el usuario pida implementar o cambiar algo del motor, del modelo de flujo y colas, de la reproducibilidad o de las pruebas del motor.
---

# Motor de simulación — SimuCity Lab

El motor es la fuente de verdad del producto. Todo lo que se escriba aquí debe ser **determinista, aislado y probado**.

## 1. Aislamiento

- Vive en `backend/engine`, módulo Java puro.
- **Prohibido** importar Spring, JPA/Hibernate, Jackson-con-anotaciones de la app, clases HTTP o cualquier cosa de `backend/app`.
- Entrada: objetos de dominio inmutables (`Network`, `ScenarioConfig`, `RunParameters`). Salida: `SimulationResult` (resumen + muestras + métricas).
- Sin I/O: no lee archivos, no escribe en base de datos, no imprime a consola. El logging lo hace quien llama.

## 2. Determinismo (obligatorio)

- Toda aleatoriedad sale de la semilla de `RunParameters`. Cada demanda tiene su propio `java.util.Random` derivado de la semilla y de su ID (`DemandState.streamSeed`), para que cambiar un semáforo o agregar otra demanda no altere las llegadas de las demás (comparación justa). Usar `java.util.Random` (algoritmo fijado por la especificación de Java) y `StrictMath` para funciones como `exp`. Nunca `Math.random()`, `new Random()` sin semilla, `UUID.randomUUID()` ni `System.currentTimeMillis()` dentro del motor.
- El tiempo simulado se cuenta en **ticks enteros** (`long tick`); el tiempo en segundos es `tick * dtSeconds`. No acumular `double` sumando `dt` en cada paso.
- Nunca iterar sobre `HashMap`/`HashSet` cuando el orden afecta el resultado. Usar listas ordenadas por ID o `LinkedHashMap` con orden de inserción estable.
- Nada de `parallelStream()` ni hilos dentro de una ejecución. Si en el futuro se paraleliza, documentar la limitación.
- Exponer `EngineVersion.CURRENT` (cadena semver) y cambiarla cuando cambie cualquier regla que altere resultados.

## 3. Orden fijo de cada paso

Cada tick ejecuta exactamente estas fases, en este orden, como métodos separados:

1. `updateSignals(tick)` — avanzar fases semafóricas.
2. `generateArrivals(tick)` — crear demanda según modo (periódico / aleatorio con semilla / perfil por periodos).
3. `computeCandidateMoves()` — calcular movimientos sobre el estado **al inicio del tick** (no sobre el estado parcialmente modificado).
4. `resolveCapacity()` — recortar movimientos según capacidad del segmento destino, semáforo y movimientos permitidos.
5. `applyMoves()` — aplicar todos los movimientos aceptados de forma consistente.
6. `updateMetrics(tick)` — tiempos, colas, viajes completados.
7. `sample(tick)` — guardar muestra solo si toca según la frecuencia configurada.

Al terminar: cerrar viajes pendientes como **incompletos** (no descartarlos) y construir el resumen.

## 4. Equidad

Cuando varios movimientos compiten por la misma capacidad, no favorecer siempre al primero de la lista. Usar un criterio explícito y documentado: reparto proporcional a la demanda, o round-robin cuyo punto de inicio rota cada tick. Si se usa aleatoriedad, que salga del generador con semilla.

## 5. Reglas del modelo (MVP: colas por segmento)

Implementación actual en `lab.simucity.engine.simulation` (`SimulationEngine` → `SimulationRun`):

- Cada calle es una cola FIFO (`QueueSource`). Un vehículo la recorre en `ceil(longitud / velocidad / dt)` ticks y luego queda "listo" (`readyTick`).
- Descarga limitada por un cubo de fichas al flujo de saturación (`lanes × saturationFlowPerLaneVph`): un vehículo solo no se demora; una cola sale a ritmo de saturación.
- Las entradas también son colas (vehículos que aún no caben en la primera calle).
- Espacio libre medido al inicio del tick (criterio conservador). Si un vehículo es rechazado, los de detrás en su misma cola también (FIFO).
- Tiempo de espera = ticks en que el vehículo estaba listo pero no avanzó.
- Anomalía `EDGE_STALLED` / `ENTRY_STALLED` solo si hay cola y **ninguna** descarga durante `stallThresholdSeconds`.

Reglas generales:

- Red = grafo dirigido. Sentidos opuestos = aristas distintas.
- Un movimiento avanza solo si: (1) el movimiento entrada→salida existe en la intersección, (2) el semáforo lo autoriza, (3) hay capacidad en el segmento siguiente, (4) el segmento no está cerrado.
- Segmento lleno bloquea la entrada desde el anterior (propagación de colas explícita).
- Costo de ruta inicial: `longitud / velocidad` (segundos). Dijkstra con pesos no negativos; A* solo con heurística admisible.
- Recalcular rutas solo cuando cambian condiciones relevantes, no en cada tick.
- Giros: `MovementRules` permite todo giro entre calles conectadas salvo el giro en U, más los prohibidos en `Scenario.prohibitedMovements`. El enrutador busca **sobre aristas** para respetarlos; cualquier cálculo de ruta debe recibir las `MovementRules` del escenario.
- Los semáforos nunca autorizan simultáneamente movimientos incompatibles: `MovementConflicts` (modelo de cuerdas en un círculo, circulación por la derecha, eje Y hacia arriba) y error `SIGNAL_CONFLICTING_MOVEMENTS`. No hay giros permisivos con cesión de paso. Para planes nuevos, partir de `SignalTemplates`.
- Demanda: tasa base + periodos (`DemandPeriod`) sin solaparse; las llegadas se derivan de la integral acumulada `DemandSpec.expectedVehiclesUntil`, calculada desde cero en cada tick.

## 6. Validación y anomalías

Antes de ejecutar, `ScenarioValidator` devuelve una lista de errores y advertencias (no lanza excepciones genéricas):

- Error: parámetros negativos, `NaN`/infinito, `dt <= 0`, duración fuera de límites, tamaño de red sobre el límite, destino inexistente.
- Advertencia: red desconectada, destinos inaccesibles desde alguna entrada.

Durante la ejecución, marcar vehículos/flujos inmóviles demasiado tiempo como anomalía. Distinguir esto de una **cola persistente legítima**, que es un resultado válido.

## 7. Métricas

Cada métrica lleva nombre, valor, unidad, alcance (red / segmento / intersección) y número de observaciones. Percentil 95 solo si hay observaciones suficientes. Reducción porcentual `(A−B)/A×100`; si `A == 0`, devolver diferencia absoluta o "no aplicable", nunca dividir por cero.

## 8. Pruebas

Toda regla nueva va acompañada de una prueba JUnit con una red pequeña de resultado conocido. Pruebas mínimas que siempre deben existir y pasar:

- Un vehículo en vía libre llega al destino en el tiempo esperado.
- Una vía cerrada no permite el paso.
- Semáforo en rojo impide el movimiento.
- Ruta inexistente se reporta; red desconectada produce advertencia.
- Nunca se supera la capacidad configurada.
- **Misma semilla + misma configuración ⇒ resultado idéntico** (comparar el resumen completo, no solo una métrica).
- Ningún tiempo, velocidad o cantidad es negativo ni no finito.

## Checklist antes de dar por terminado un cambio en el motor

- [ ] Sin dependencias fuera de `backend/engine`.
- [ ] Sin fuentes de aleatoriedad o tiempo real no controladas.
- [ ] Orden de fases respetado.
- [ ] Pruebas nuevas + prueba de determinismo pasando.
- [ ] `EngineVersion.CURRENT` actualizada si cambió el resultado.
- [ ] Unidades SI y documentadas en el código público.
