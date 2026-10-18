package lab.simucity.engine.simulation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import lab.simucity.engine.EngineVersion;
import lab.simucity.engine.metrics.Metric;
import lab.simucity.engine.metrics.MetricNames;
import lab.simucity.engine.metrics.MetricScope;
import lab.simucity.engine.network.Edge;
import lab.simucity.engine.network.MovementRules;
import lab.simucity.engine.routing.DijkstraRouter;
import lab.simucity.engine.routing.Route;
import lab.simucity.engine.validation.ValidationIssue;

/**
 * Una ejecución del modelo de colas por segmento. Se usa una sola vez.
 *
 * <p>Reglas de determinismo: todas las colecciones que se recorren están ordenadas por ID o son
 * {@link LinkedHashMap} con orden de inserción derivado de listas ordenadas; los {@link HashMap}
 * solo se consultan por clave.
 */
final class SimulationRun {

    private final RunParameters params;
    private final List<ValidationIssue> warnings;
    private final double dt;
    private final long durationTicks;
    private final long sampleEveryTicks;
    private final long stallTicks;

    /** Calles ordenadas por ID, luego entradas ordenadas por ID de su primera calle. */
    private final List<QueueSource> sources = new ArrayList<>();
    private final List<QueueSource> edgeSources = new ArrayList<>();
    private final List<QueueSource> entrySources = new ArrayList<>();
    private final List<DemandState> demands = new ArrayList<>();
    private final List<SignalState> signals = new ArrayList<>();
    private final Map<String, SignalState> signalByNode = new HashMap<>();

    /** Vehículos admitidos en cada calle durante el tick, en el orden en que fueron admitidos. */
    private final Map<QueueSource, List<Candidate>> admittedByTarget = new LinkedHashMap<>();

    private final List<Long> completedTravelTicks = new ArrayList<>();
    private long completedWaitTicks;
    private long generated;
    private long nextVehicleId;
    private final List<Sample> samples = new ArrayList<>();
    private final List<Anomaly> anomalies = new ArrayList<>();

    SimulationRun(Scenario scenario, RunParameters params, List<ValidationIssue> warnings) {
        this.params = params;
        this.warnings = warnings;
        this.dt = params.dtSeconds();
        this.durationTicks = params.durationTicks();
        this.sampleEveryTicks = params.sampleEveryTicks();
        this.stallTicks = params.stallTicks();

        double dischargePerLanePerTick = params.saturationFlowPerLaneVph() * dt / 3600.0;
        Map<String, QueueSource> edgeById = new HashMap<>();
        List<Edge> sortedEdges = scenario.network().edges().stream()
                .sorted(Comparator.comparing(Edge::id))
                .toList();
        for (Edge edge : sortedEdges) {
            QueueSource source = QueueSource.forEdge(edge, travelTicks(edge), edge.lanes() * dischargePerLanePerTick);
            edgeSources.add(source);
            edgeById.put(edge.id(), source);
        }

        DijkstraRouter router = new DijkstraRouter();
        MovementRules rules = scenario.movementRules();
        TreeMap<String, QueueSource> entriesByFirstEdge = new TreeMap<>();
        List<DemandSpec> sortedDemands = scenario.demands().stream()
                .sorted(Comparator.comparing(DemandSpec::id))
                .toList();
        for (DemandSpec spec : sortedDemands) {
            Optional<Route> route = router.findRoute(scenario.network(), spec.originNodeId(),
                    spec.destinationNodeId(), rules);
            if (route.isEmpty() || route.get().edgeIds().isEmpty()) {
                continue; // El validador ya emitió DEMAND_NO_ROUTE.
            }
            List<QueueSource> path = route.get().edgeIds().stream().map(edgeById::get).toList();
            QueueSource entry = entriesByFirstEdge.computeIfAbsent(path.getFirst().id,
                    k -> QueueSource.forEntry(path.getFirst()));
            demands.add(new DemandState(spec, path, entry, params.seed(), dt));
        }
        entrySources.addAll(entriesByFirstEdge.values());
        sources.addAll(edgeSources);
        sources.addAll(entrySources);

        scenario.signals().stream()
                .sorted(Comparator.comparing(SignalPlan::nodeId))
                .forEach(plan -> {
                    SignalState state = new SignalState(plan, dt);
                    signals.add(state);
                    signalByNode.put(plan.nodeId(), state);
                });
    }

    SimulationResult execute(RunControl control) {
        for (long tick = 0; tick < durationTicks; tick++) {
            if (tick % RunControl.CHECK_EVERY_TICKS == 0 && control.shouldStop()) {
                throw new SimulationAbortedException(tick);
            }
            updateSignals(tick);
            generateArrivals(tick);
            computeCandidateMoves(tick);
            resolveCapacity(tick);
            applyMoves(tick);
            updateMetrics(tick);
            sample(tick);
        }
        return buildResult();
    }

    // 1. Semáforos ---------------------------------------------------------------------------------

    private void updateSignals(long tick) {
        for (SignalState signal : signals) {
            signal.update(tick);
        }
    }

    // 2. Llegadas ----------------------------------------------------------------------------------

    private void generateArrivals(long tick) {
        for (DemandState demand : demands) {
            int count = demand.arrivals(tick);
            for (int i = 0; i < count; i++) {
                demand.entry.vehicles.addLast(new Vehicle(nextVehicleId++, demand.path, tick));
                generated++;
            }
        }
    }

    // 3. Movimientos candidatos (sobre el estado al inicio del tick) -------------------------------

    private void computeCandidateMoves(long tick) {
        for (QueueSource source : sources) {
            source.candidates.clear();
            source.acceptedCount = 0;
            source.tokens = Math.min(source.tokenCap, source.tokens + source.dischargePerTick);
            int allowance = (int) Math.floor(source.tokens);

            for (Vehicle vehicle : source.vehicles) {
                if (source.candidates.size() >= allowance || vehicle.readyTick > tick) {
                    break;
                }
                QueueSource target = vehicle.nextTarget();
                if (target != null && !source.entry && !signalAllows(source.edge, target.edge)) {
                    break; // FIFO: si el primero espera el verde, los de atrás también.
                }
                source.candidates.add(new Candidate(vehicle, target));
            }
        }
    }

    private boolean signalAllows(Edge from, Edge to) {
        SignalState signal = signalByNode.get(from.toNodeId());
        return signal == null || signal.allows(from.id(), to.id());
    }

    // 4. Capacidad ---------------------------------------------------------------------------------

    /**
     * Reparte el espacio libre de cada calle entre las colas que quieren entrar en ella mediante
     * round-robin, cuyo punto de inicio rota en cada tick para no favorecer siempre a la misma cola.
     * El espacio libre se mide al inicio del tick (criterio conservador: no se cuenta el espacio
     * que liberan los vehículos que salen en este mismo tick).
     */
    private void resolveCapacity(long tick) {
        admittedByTarget.clear();
        Map<QueueSource, Map<QueueSource, ArrayDeque<Candidate>>> requests = new LinkedHashMap<>();
        for (QueueSource source : sources) {
            for (Candidate candidate : source.candidates) {
                if (candidate.target == null) {
                    candidate.accepted = true; // Salir de la red no requiere capacidad.
                } else {
                    requests.computeIfAbsent(candidate.target, k -> new LinkedHashMap<>())
                            .computeIfAbsent(source, k -> new ArrayDeque<>())
                            .add(candidate);
                }
            }
        }

        for (Map.Entry<QueueSource, Map<QueueSource, ArrayDeque<Candidate>>> request : requests.entrySet()) {
            QueueSource target = request.getKey();
            List<ArrayDeque<Candidate>> queues = new ArrayList<>(request.getValue().values());
            int space = target.storageCapacity() - target.vehicles.size();
            int start = (int) (tick % queues.size());
            List<Candidate> admitted = new ArrayList<>();

            boolean progress = true;
            while (space > 0 && progress) {
                progress = false;
                for (int i = 0; i < queues.size() && space > 0; i++) {
                    ArrayDeque<Candidate> queue = queues.get((start + i) % queues.size());
                    if (!queue.isEmpty()) {
                        Candidate candidate = queue.poll();
                        candidate.accepted = true;
                        admitted.add(candidate);
                        space--;
                        progress = true;
                    }
                }
            }
            admittedByTarget.put(target, admitted);
        }

        // FIFO: un vehículo no puede adelantar a otro de su misma cola que fue rechazado.
        for (QueueSource source : sources) {
            boolean blocked = false;
            for (Candidate candidate : source.candidates) {
                if (blocked || !candidate.accepted) {
                    blocked = true;
                    candidate.accepted = false;
                } else {
                    source.acceptedCount++;
                }
            }
        }
        for (List<Candidate> admitted : admittedByTarget.values()) {
            admitted.removeIf(c -> !c.accepted);
        }
    }

    // 5. Aplicar movimientos -----------------------------------------------------------------------

    private void applyMoves(long tick) {
        for (QueueSource source : sources) {
            for (int i = 0; i < source.acceptedCount; i++) {
                Candidate candidate = source.candidates.get(i);
                Vehicle vehicle = source.vehicles.pollFirst();
                assert vehicle == candidate.vehicle;
                vehicle.waitTicks += tick - vehicle.readyTick;
                if (candidate.target == null) {
                    completeTrip(vehicle, tick);
                }
            }
            source.discharged += source.acceptedCount;
            source.tokens -= source.acceptedCount;
        }
        for (Map.Entry<QueueSource, List<Candidate>> entry : admittedByTarget.entrySet()) {
            QueueSource target = entry.getKey();
            for (Candidate candidate : entry.getValue()) {
                Vehicle vehicle = candidate.vehicle;
                vehicle.position++;
                vehicle.readyTick = tick + target.travelTicks;
                target.vehicles.addLast(vehicle);
            }
        }
    }

    private void completeTrip(Vehicle vehicle, long tick) {
        completedTravelTicks.add(tick - vehicle.generatedTick);
        completedWaitTicks += vehicle.waitTicks;
    }

    // 6. Métricas ----------------------------------------------------------------------------------

    private void updateMetrics(long tick) {
        for (QueueSource source : sources) {
            int queued = 0;
            for (Vehicle vehicle : source.vehicles) {
                if (vehicle.readyTick > tick) {
                    break; // readyTick es no decreciente dentro de cada cola.
                }
                queued++;
            }
            source.currentQueue = queued;
            source.queueSumTicks += queued;
            source.maxQueue = Math.max(source.maxQueue, queued);
            detectStall(source, tick, queued);
        }
    }

    /** Bloqueo = hay cola y nadie sale durante el umbral. Una cola lenta pero que avanza no lo es. */
    private void detectStall(QueueSource source, long tick, int queued) {
        if (queued == 0 || source.acceptedCount > 0) {
            source.stalledSinceTick = -1;
            return;
        }
        if (source.stalledSinceTick < 0) {
            source.stalledSinceTick = tick;
        }
        if (!source.stallReported && tick - source.stalledSinceTick + 1 >= stallTicks) {
            source.stallReported = true;
            double since = source.stalledSinceTick * dt;
            if (source.entry) {
                anomalies.add(new Anomaly("ENTRY_STALLED", "Los vehículos no pueden ingresar a la calle '"
                        + source.id + "' desde el segundo " + since + ": la calle está llena.", source.id, since));
            } else {
                anomalies.add(new Anomaly("EDGE_STALLED", "La calle '" + source.id
                        + "' tiene vehículos detenidos sin avanzar desde el segundo " + since
                        + " (posible bloqueo).", source.id, since));
            }
        }
    }

    // 7. Muestras ----------------------------------------------------------------------------------

    private void sample(long tick) {
        if (tick % sampleEveryTicks != 0) {
            return;
        }
        List<Sample.EdgeSample> edges = new ArrayList<>(edgeSources.size());
        int inNetwork = 0;
        int queued = 0;
        for (QueueSource source : edgeSources) {
            edges.add(new Sample.EdgeSample(source.id, source.vehicles.size(), source.currentQueue));
            inNetwork += source.vehicles.size();
            queued += source.currentQueue;
        }
        int atEntries = 0;
        for (QueueSource source : entrySources) {
            atEntries += source.vehicles.size();
        }
        samples.add(new Sample(tick, tick * dt, inNetwork, queued, atEntries, completedTravelTicks.size(), edges));
    }

    // Resultado ------------------------------------------------------------------------------------

    private SimulationResult buildResult() {
        List<Metric> metrics = new ArrayList<>();
        long completed = completedTravelTicks.size();
        double durationSeconds = durationTicks * dt;

        metrics.add(network(MetricNames.TRIPS_GENERATED, (double) generated, "veh", generated));
        metrics.add(network(MetricNames.TRIPS_COMPLETED, (double) completed, "veh", completed));
        metrics.add(network(MetricNames.TRIPS_INCOMPLETE, (double) (generated - completed), "veh", generated));

        long travelSum = 0;
        for (long ticks : completedTravelTicks) {
            travelSum += ticks;
        }
        metrics.add(network(MetricNames.TRAVEL_TIME_MEAN,
                completed == 0 ? null : travelSum / (double) completed * dt, "s", completed));
        metrics.add(network(MetricNames.TRAVEL_TIME_P95, percentile95(completed), "s", completed));
        metrics.add(network(MetricNames.WAIT_TIME_MEAN,
                completed == 0 ? null : completedWaitTicks / (double) completed * dt, "s", completed));
        metrics.add(network(MetricNames.THROUGHPUT, completed / durationSeconds * 3600.0, "veh/h", completed));

        long queueSum = 0;
        for (QueueSource source : sources) {
            queueSum += source.queueSumTicks;
        }
        metrics.add(network(MetricNames.QUEUE_MEAN, queueSum / (double) durationTicks, "veh", durationTicks));

        for (QueueSource source : edgeSources) {
            metrics.add(segment(MetricNames.QUEUE_MAX, source.maxQueue, "veh", source.id, durationTicks));
            metrics.add(segment(MetricNames.QUEUE_MEAN, source.queueSumTicks / (double) durationTicks, "veh",
                    source.id, durationTicks));
            metrics.add(segment(MetricNames.VEHICLES_DISCHARGED, source.discharged, "veh", source.id,
                    source.discharged));
            metrics.add(segment(MetricNames.UTILIZATION,
                    source.discharged / (source.dischargePerTick * durationTicks), "ratio", source.id, durationTicks));
        }
        for (QueueSource source : entrySources) {
            metrics.add(new Metric(MetricNames.QUEUE_MAX, (double) source.maxQueue, "veh", MetricScope.ENTRY,
                    source.id, durationTicks));
        }

        return new SimulationResult(EngineVersion.CURRENT, params, durationTicks, metrics, samples, anomalies,
                warnings);
    }

    /** Percentil 95 por el método de rango más cercano. */
    private Double percentile95(long completed) {
        if (completed < MetricNames.MIN_OBSERVATIONS_P95) {
            return null;
        }
        List<Long> sorted = new ArrayList<>(completedTravelTicks);
        sorted.sort(null);
        int index = (int) Math.ceil(0.95 * completed) - 1;
        return sorted.get(index) * dt;
    }

    private long travelTicks(Edge edge) {
        return Math.max(1, (long) Math.ceil(edge.freeFlowTravelTimeSeconds() / dt - 1e-9));
    }

    private static Metric network(String name, Double value, String unit, long observations) {
        return new Metric(name, value, unit, MetricScope.NETWORK, null, observations);
    }

    private static Metric segment(String name, double value, String unit, String edgeId, long observations) {
        return new Metric(name, value, unit, MetricScope.SEGMENT, edgeId, observations);
    }
}
