package lab.simucity.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import lab.simucity.engine.metrics.MetricNames;
import lab.simucity.engine.network.Edge;
import lab.simucity.engine.network.MovementRules;
import lab.simucity.engine.network.Network;
import lab.simucity.engine.network.Node;
import lab.simucity.engine.network.NodeType;
import lab.simucity.engine.simulation.ArrivalMode;
import lab.simucity.engine.simulation.DemandSpec;
import lab.simucity.engine.simulation.RunParameters;
import lab.simucity.engine.simulation.Scenario;
import lab.simucity.engine.simulation.SignalPlan;
import lab.simucity.engine.simulation.SignalTemplates;
import lab.simucity.engine.simulation.SimulationEngine;
import lab.simucity.engine.simulation.SimulationResult;

/**
 * Peor caso que admite el servidor (sección 15): cuadrícula de 20×20 intersecciones con semáforo en todas,
 * calles de doble sentido (1520 aristas), 20 orígenes y destinos, ~48 000 vehículos y 2 horas simuladas.
 *
 * <p>No se ejecuta en la suite normal: {@code ./mvnw -pl engine test -Dperf=true -Dtest=PerformanceTest}.
 */
@EnabledIfSystemProperty(named = "perf", matches = "true")
class PerformanceTest {

    private static final int SIZE = 20;
    private static final double BLOCK_METERS = 150;
    private static final double SERVER_TIMEOUT_SECONDS = 120;

    @Test
    void redMaximaDelServidor_seSimulaMuyPorDebajoDelTiempoMaximo() {
        Scenario scenario = grid();
        RunParameters params = new RunParameters(42, 0.5, 7200, 30, 1800, 300);
        SimulationEngine engine = new SimulationEngine();

        engine.run(scenario, new RunParameters(1, 0.5, 600, 30, 1800, 300)); // calentamiento de la JVM

        long start = System.nanoTime();
        SimulationResult result = engine.run(scenario, params);
        double seconds = (System.nanoTime() - start) / 1e9;

        double generated = result.metric(MetricNames.TRIPS_GENERATED).orElseThrow().value();
        double completed = result.metric(MetricNames.TRIPS_COMPLETED).orElseThrow().value();
        System.out.printf("%n[rendimiento] %d nodos, %d calles, %d semáforos, %d ticks: %.2f s reales%n",
                scenario.network().nodes().size(), scenario.network().edges().size(), scenario.signals().size(),
                result.durationTicks(), seconds);
        System.out.printf("[rendimiento] %.0f vehículos generados, %.0f completados, %d métricas, %d muestras%n",
                generated, completed, result.metrics().size(), result.samples().size());

        assertTrue(seconds < SERVER_TIMEOUT_SECONDS / 4,
                "Debe sobrar margen respecto al tiempo máximo del servidor: " + seconds + " s");
    }

    private static Scenario grid() {
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                nodes.add(new Node(id(r, c), NodeType.INTERSECTION, c * BLOCK_METERS, r * BLOCK_METERS));
                if (c > 0) {
                    twoWay(edges, id(r, c - 1), id(r, c));
                }
                if (r > 0) {
                    twoWay(edges, id(r - 1, c), id(r, c));
                }
            }
        }
        List<DemandSpec> demands = new ArrayList<>();
        for (int r = 0; r < SIZE; r++) {
            String entry = "E" + r;
            String exit = "X" + r;
            nodes.add(new Node(entry, NodeType.ENTRY, -BLOCK_METERS, r * BLOCK_METERS));
            nodes.add(new Node(exit, NodeType.EXIT, SIZE * BLOCK_METERS, r * BLOCK_METERS));
            edges.add(edge(entry, id(r, 0)));
            edges.add(edge(id(r, SIZE - 1), exit));
            // Cada fila envía tráfico a la salida de la fila opuesta: muchas rutas cruzan la cuadrícula.
            demands.add(new DemandSpec("d" + r, entry, "X" + (SIZE - 1 - r), 20, ArrivalMode.POISSON));
        }
        Network network = new Network(nodes, edges);
        List<SignalPlan> signals = new ArrayList<>();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                signals.add(SignalTemplates.splitPhasing(network, id(r, c), MovementRules.DEFAULT, 20, 2));
            }
        }
        return new Scenario(network, demands, signals);
    }

    private static void twoWay(List<Edge> edges, String a, String b) {
        edges.add(edge(a, b));
        edges.add(edge(b, a));
    }

    private static Edge edge(String from, String to) {
        return new Edge(from + ">" + to, from, to, BLOCK_METERS, 2, 13.9, 40, false);
    }

    private static String id(int r, int c) {
        return "N" + r + "_" + c;
    }
}
