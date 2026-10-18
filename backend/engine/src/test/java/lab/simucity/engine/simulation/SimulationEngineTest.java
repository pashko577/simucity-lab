package lab.simucity.engine.simulation;

import static lab.simucity.engine.metrics.MetricNames.QUEUE_MAX;
import static lab.simucity.engine.metrics.MetricNames.TRAVEL_TIME_MEAN;
import static lab.simucity.engine.metrics.MetricNames.TRIPS_COMPLETED;
import static lab.simucity.engine.metrics.MetricNames.TRIPS_GENERATED;
import static lab.simucity.engine.metrics.MetricNames.TRIPS_INCOMPLETE;
import static lab.simucity.engine.metrics.MetricNames.WAIT_TIME_MEAN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import lab.simucity.engine.EngineVersion;
import lab.simucity.engine.TestNetworks;
import lab.simucity.engine.metrics.Metric;
import lab.simucity.engine.network.Edge;
import lab.simucity.engine.network.Movement;
import lab.simucity.engine.network.Network;
import lab.simucity.engine.network.Node;
import lab.simucity.engine.network.NodeType;

class SimulationEngineTest {

    private final SimulationEngine engine = new SimulationEngine();

    // Con 1 veh/min y dt = 0,5 s, el único vehículo se genera en el tick 119 (segundo 59,5).
    private static DemandSpec oneVehiclePerMinute(String origin, String destination) {
        return new DemandSpec("d1", origin, destination, 1.0, ArrivalMode.PERIODIC);
    }

    @Test
    void vehiculoUnico_viaLibre_llegaEnElTiempoEsperado() {
        Scenario scenario = new Scenario(TestNetworks.straightRoad(), List.of(oneVehiclePerMinute("E", "X")), List.of());

        SimulationResult result = engine.run(scenario, RunParameters.defaults(42, 100));

        assertEquals(1.0, value(result, TRIPS_GENERATED));
        assertEquals(1.0, value(result, TRIPS_COMPLETED));
        assertEquals(30.0, value(result, TRAVEL_TIME_MEAN), 1e-9); // 100 m + 200 m a 10 m/s
        assertEquals(0.0, value(result, WAIT_TIME_MEAN), 1e-9);
        assertEquals(EngineVersion.CURRENT, result.engineVersion());
    }

    @Test
    void viaCerrada_losVehiculosUsanElDesvio() {
        Network network = TestNetworks.builder()
                .node("E", NodeType.ENTRY)
                .node("D", NodeType.INTERSECTION)
                .node("X", NodeType.EXIT)
                .edge(new Edge("directa", "E", "X", 100, 1, 10, 10, true))
                .edge("d1", "E", "D", 100, 10)
                .edge("d2", "D", "X", 100, 10)
                .build();
        Scenario scenario = new Scenario(network, List.of(oneVehiclePerMinute("E", "X")), List.of());

        SimulationResult result = engine.run(scenario, RunParameters.defaults(42, 100));

        assertEquals(1.0, value(result, TRIPS_COMPLETED));
        assertEquals(20.0, value(result, TRAVEL_TIME_MEAN), 1e-9);
        assertEquals(1.0, value(result, "vehicles_discharged", "d1"));
        assertEquals(0.0, value(result, "vehicles_discharged", "directa"));
    }

    @Test
    void viaCerrada_sinDesvio_seAdvierteYNoSeGeneranVehiculos() {
        Network network = TestNetworks.builder()
                .node("E", NodeType.ENTRY)
                .node("X", NodeType.EXIT)
                .edge(new Edge("e1", "E", "X", 100, 1, 10, 10, true))
                .build();
        Scenario scenario = new Scenario(network, List.of(oneVehiclePerMinute("E", "X")), List.of());

        SimulationResult result = engine.run(scenario, RunParameters.defaults(42, 100));

        assertTrue(result.warnings().stream().anyMatch(w -> w.code().equals("DEMAND_NO_ROUTE")));
        assertEquals(0.0, value(result, TRIPS_GENERATED));
    }

    @Test
    void semaforoEnRojo_retieneAlVehiculoHastaElVerde() {
        // Ciclo de 80 s: verde 10 s (ticks 0-19), rojo 70 s (ticks 20-159).
        // El vehículo llega al semáforo en el tick 139 (rojo) y pasa en el tick 160 (verde): espera 21 ticks.
        SignalPlan plan = new SignalPlan("I", 0, List.of(
                new SignalPhase(10, List.of(new Movement("e1", "e2"))),
                new SignalPhase(70, List.of())));
        Scenario scenario = new Scenario(TestNetworks.straightRoad(), List.of(oneVehiclePerMinute("E", "X")),
                List.of(plan));

        SimulationResult result = engine.run(scenario, RunParameters.defaults(42, 150));

        assertEquals(1.0, value(result, TRIPS_COMPLETED));
        assertEquals(10.5, value(result, WAIT_TIME_MEAN), 1e-9);
        assertEquals(40.5, value(result, TRAVEL_TIME_MEAN), 1e-9); // 30 s de recorrido + 10,5 s de espera
    }

    @Test
    void semaforoSiempreEnRojo_nadiePasaYSeReportaBloqueo() {
        SignalPlan allRed = new SignalPlan("I", 0, List.of(new SignalPhase(60, List.of())));
        Scenario scenario = new Scenario(TestNetworks.straightRoad(),
                List.of(new DemandSpec("d1", "E", "X", 6, ArrivalMode.PERIODIC)), List.of(allRed));

        SimulationResult result = engine.run(scenario, RunParameters.defaults(42, 900));

        assertEquals(0.0, value(result, TRIPS_COMPLETED));
        assertTrue(value(result, TRIPS_INCOMPLETE) > 0);
        assertTrue(result.anomalies().stream()
                .anyMatch(a -> a.code().equals("EDGE_STALLED") && a.elementId().equals("e1")));
        assertTrue(result.anomalies().stream()
                .anyMatch(a -> a.code().equals("ENTRY_STALLED") && a.elementId().equals("e1")));
    }

    @Test
    void colaPersistenteQueAvanza_noEsAnomalia() {
        // 40 veh/min supera la capacidad de 1 carril (1800 veh/h = 30 veh/min): la cola crece, pero se mueve.
        Scenario scenario = new Scenario(TestNetworks.straightRoad(),
                List.of(new DemandSpec("d1", "E", "X", 40, ArrivalMode.PERIODIC)), List.of());

        SimulationResult result = engine.run(scenario, RunParameters.defaults(42, 900));

        assertTrue(result.anomalies().isEmpty(), () -> "Anomalías inesperadas: " + result.anomalies());
        assertTrue(value(result, TRIPS_INCOMPLETE) > 100);
        double entryQueueMax = result.metrics().stream()
                .filter(m -> m.name().equals(QUEUE_MAX) && m.scope() == lab.simucity.engine.metrics.MetricScope.ENTRY)
                .findFirst().orElseThrow().value();
        assertTrue(entryQueueMax > 100);
    }

    @Test
    void capacidadDeAlmacenamiento_nuncaSeSupera() {
        Network network = TestNetworks.builder()
                .node("E", NodeType.ENTRY)
                .node("I", NodeType.INTERSECTION)
                .node("X", NodeType.EXIT)
                .edge(new Edge("e1", "E", "I", 100, 2, 10, 3, false))
                .edge(new Edge("e2", "I", "X", 300, 1, 5, 2, false))
                .build();
        SignalPlan plan = new SignalPlan("I", 0, List.of(
                new SignalPhase(60, List.of()),
                new SignalPhase(20, List.of(new Movement("e1", "e2")))));
        Scenario scenario = new Scenario(network,
                List.of(new DemandSpec("d1", "E", "X", 30, ArrivalMode.POISSON)), List.of(plan));
        RunParameters params = RunParameters.defaults(7, 1200).withSampleIntervalSeconds(0.5);

        SimulationResult result = engine.run(scenario, params);

        int maxE1 = 0;
        for (Sample sample : result.samples()) {
            for (Sample.EdgeSample edge : sample.edges()) {
                int capacity = edge.edgeId().equals("e1") ? 3 : 2;
                assertTrue(edge.vehicles() <= capacity,
                        () -> "Capacidad superada en " + edge.edgeId() + " (tick " + sample.tick() + ")");
                if (edge.edgeId().equals("e1")) {
                    maxE1 = Math.max(maxE1, edge.vehicles());
                }
            }
        }
        assertEquals(3, maxE1, "La prueba debe llegar a llenar la calle para ser significativa");
    }

    @Test
    void mismaSemillaYConfiguracion_resultadoIdentico() {
        Scenario scenario = busyScenario();
        RunParameters params = RunParameters.defaults(12345, 1800);

        SimulationResult first = engine.run(scenario, params);
        SimulationResult second = engine.run(scenario, params);

        assertEquals(first, second);
    }

    @Test
    void semillaDistinta_resultadoDistinto() {
        Scenario scenario = busyScenario();

        SimulationResult a = engine.run(scenario, RunParameters.defaults(1, 1800));
        SimulationResult b = engine.run(scenario, RunParameters.defaults(2, 1800));

        assertNotEquals(a.metrics(), b.metrics());
    }

    @Test
    void ordenDeCreacionDeElementos_noAfectaElResultado() {
        Scenario reference = busyScenario();
        SimulationResult expected = engine.run(reference, RunParameters.defaults(99, 1800));

        for (int i = 0; i < 5; i++) {
            Random shuffler = new Random(i);
            List<Node> nodes = new ArrayList<>(reference.network().nodes());
            List<Edge> edges = new ArrayList<>(reference.network().edges());
            List<DemandSpec> demands = new ArrayList<>(reference.demands());
            Collections.shuffle(nodes, shuffler);
            Collections.shuffle(edges, shuffler);
            Collections.shuffle(demands, shuffler);
            Scenario shuffled = new Scenario(new Network(nodes, edges), demands, reference.signals());

            SimulationResult actual = engine.run(shuffled, RunParameters.defaults(99, 1800));

            assertEquals(expected.metrics(), actual.metrics(), "Métricas distintas en la iteración " + i);
            assertEquals(expected.samples(), actual.samples(), "Muestras distintas en la iteración " + i);
        }
    }

    @Test
    void agregarUnaDemanda_noCambiaLasLlegadasDeOtra() {
        Scenario base = busyScenario();
        List<DemandSpec> extended = new ArrayList<>(base.demands());
        extended.add(new DemandSpec("zz-extra", "E2", "X1", 5, ArrivalMode.POISSON));

        DemandSpec target = base.demands().getFirst();
        Scenario onlyTarget = new Scenario(base.network(), List.of(target), base.signals());
        Scenario targetPlusExtra = new Scenario(base.network(),
                List.of(target, extended.getLast()), base.signals());

        double generatedAlone = value(engine.run(onlyTarget, RunParameters.defaults(5, 1800)), TRIPS_GENERATED);
        double generatedWithExtra = value(engine.run(targetPlusExtra, RunParameters.defaults(5, 1800)), TRIPS_GENERATED);
        double extraAlone = value(engine.run(new Scenario(base.network(), List.of(extended.getLast()), base.signals()),
                RunParameters.defaults(5, 1800)), TRIPS_GENERATED);

        assertEquals(generatedAlone + extraAlone, generatedWithExtra);
    }

    @Test
    void metricasYMuestras_sonFinitasYNoNegativas() {
        SimulationResult result = engine.run(busyScenario(), RunParameters.defaults(3, 1800));

        for (Metric metric : result.metrics()) {
            if (metric.isApplicable()) {
                assertTrue(Double.isFinite(metric.value()) && metric.value() >= 0, () -> "Métrica inválida: " + metric);
            }
            assertTrue(metric.observations() >= 0);
        }
        for (Sample sample : result.samples()) {
            assertTrue(sample.vehiclesInNetwork() >= 0 && sample.queuedVehicles() >= 0
                    && sample.waitingAtEntries() >= 0 && sample.completedTrips() >= 0);
            assertTrue(sample.queuedVehicles() <= sample.vehiclesInNetwork());
        }
        assertTrue(value(result, TRIPS_COMPLETED) > 0, "El escenario debe producir viajes para ser significativo");
    }

    @Test
    void escenarioInvalido_lanzaExcepcionConTodosLosErrores() {
        Scenario scenario = new Scenario(TestNetworks.straightRoad(), List.of(
                new DemandSpec("negativa", "E", "X", -1, ArrivalMode.PERIODIC),
                new DemandSpec("alReves", "X", "E", 1, ArrivalMode.PERIODIC)), List.of());
        RunParameters params = new RunParameters(1, 0, 100, 5, 1800, 300);

        InvalidScenarioException ex = assertThrows(InvalidScenarioException.class, () -> engine.run(scenario, params));

        assertTrue(ex.validation().hasCode("PARAM_INVALID_DT"));
        assertTrue(ex.validation().hasCode("DEMAND_INVALID_RATE"));
        assertTrue(ex.validation().hasCode("DEMAND_ORIGIN_NOT_ENTRY"));
        assertTrue(ex.validation().hasCode("DEMAND_DESTINATION_NOT_EXIT"));
    }

    @Test
    void semaforoConMovimientoAjeno_esError() {
        SignalPlan plan = new SignalPlan("I", 0, List.of(new SignalPhase(30, List.of(new Movement("e2", "e1")))));
        Scenario scenario = new Scenario(TestNetworks.straightRoad(), List.of(), List.of(plan));

        InvalidScenarioException ex = assertThrows(InvalidScenarioException.class,
                () -> engine.run(scenario, RunParameters.defaults(1, 100)));

        assertTrue(ex.validation().hasCode("SIGNAL_INVALID_MOVEMENT"));
    }

    /**
     * Dos entradas que confluyen en I2 controlada por semáforo, más un giro hacia X2 en I1.
     * <pre>
     *   E1 → I1 → I2 → X1
     *        ↓    ↑
     *        X2   E2
     * </pre>
     */
    private static Scenario busyScenario() {
        Network network = TestNetworks.builder()
                .node("E1", NodeType.ENTRY)
                .node("E2", NodeType.ENTRY)
                .node("I1", NodeType.INTERSECTION)
                .node("I2", NodeType.INTERSECTION)
                .node("X1", NodeType.EXIT)
                .node("X2", NodeType.EXIT)
                .edge(new Edge("a", "E1", "I1", 150, 2, 12, 20, false))
                .edge(new Edge("b", "I1", "I2", 200, 1, 12, 15, false))
                .edge(new Edge("c", "I2", "X1", 150, 2, 12, 20, false))
                .edge(new Edge("d", "E2", "I2", 120, 1, 10, 10, false))
                .edge(new Edge("e", "I1", "X2", 100, 1, 10, 10, false))
                .build();
        SignalPlan plan = new SignalPlan("I2", 0, List.of(
                new SignalPhase(30, List.of(new Movement("b", "c"))),
                new SignalPhase(4, List.of()),
                new SignalPhase(25, List.of(new Movement("d", "c"))),
                new SignalPhase(4, List.of())));
        List<DemandSpec> demands = List.of(
                new DemandSpec("d-e1-x1", "E1", "X1", 14, ArrivalMode.POISSON),
                new DemandSpec("d-e1-x2", "E1", "X2", 6, ArrivalMode.POISSON),
                new DemandSpec("d-e2-x1", "E2", "X1", 10, ArrivalMode.POISSON));
        return new Scenario(network, demands, List.of(plan));
    }

    private static double value(SimulationResult result, String name) {
        return result.metric(name).orElseThrow().value();
    }

    private static double value(SimulationResult result, String name, String scopeId) {
        return result.metric(name, scopeId).orElseThrow().value();
    }

    @Test
    void control_detieneLaEjecucion() {
        Scenario scenario = busyScenario();
        int[] checks = {0};
        RunControl stopOnThirdCheck = () -> ++checks[0] >= 3;

        SimulationAbortedException ex = assertThrows(SimulationAbortedException.class,
                () -> engine.run(scenario, RunParameters.defaults(1, 1800), stopOnThirdCheck));

        assertEquals(2L * RunControl.CHECK_EVERY_TICKS, ex.tick());
    }

    @Test
    void control_queNuncaDetiene_noCambiaElResultado() {
        Scenario scenario = busyScenario();
        RunParameters params = RunParameters.defaults(77, 1800);

        assertEquals(engine.run(scenario, params), engine.run(scenario, params, () -> false));
    }

    @Test
    void reduccionPorcentual_baseCeroNoAplica() {
        assertEquals(25.0, lab.simucity.engine.metrics.Comparison.percentReduction(40, 30).orElseThrow(), 1e-9);
        assertFalse(lab.simucity.engine.metrics.Comparison.percentReduction(0, 5).isPresent());
    }
}
