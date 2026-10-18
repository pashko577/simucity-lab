package lab.simucity.engine.simulation;

import static lab.simucity.engine.metrics.MetricNames.TRIPS_COMPLETED;
import static lab.simucity.engine.metrics.MetricNames.TRIPS_GENERATED;
import static lab.simucity.engine.metrics.MetricNames.VEHICLES_DISCHARGED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import lab.simucity.engine.TestNetworks;
import lab.simucity.engine.network.Movement;
import lab.simucity.engine.network.Network;
import lab.simucity.engine.network.NodeType;

class DemandProfileAndTurnsTest {

    private final SimulationEngine engine = new SimulationEngine();

    @Test
    void perfilPeriodico_generaSoloDentroDelPeriodo() {
        // Base 0; de 60 a 120 s, 30 veh/min ⇒ exactamente 30 vehículos, ninguno antes del segundo 60.
        DemandSpec demand = new DemandSpec("d1", "E", "X", 0, ArrivalMode.PERIODIC,
                List.of(new DemandPeriod(60, 120, 30)));
        Scenario scenario = new Scenario(TestNetworks.straightRoad(), List.of(demand), List.of());

        assertEquals(0.0, value(engine.run(scenario, RunParameters.defaults(1, 60)), TRIPS_GENERATED));
        assertEquals(30.0, value(engine.run(scenario, RunParameters.defaults(1, 300)), TRIPS_GENERATED));
    }

    @Test
    void perfilPoisson_horaPuntaGeneraLaCantidadEsperada() {
        // Esperado: 30 veh/min × 30 min + 5 veh/min × 30 min = 1050 vehículos (desviación típica ≈ 32).
        Network wide = TestNetworks.builder()
                .node("E", NodeType.ENTRY)
                .node("X", NodeType.EXIT)
                .edge(new lab.simucity.engine.network.Edge("e1", "E", "X", 100, 3, 10, 50, false))
                .build();
        DemandSpec demand = new DemandSpec("d1", "E", "X", 5, ArrivalMode.POISSON,
                List.of(new DemandPeriod(0, 1800, 30)));
        Scenario scenario = new Scenario(wide, List.of(demand), List.of());

        double generated = value(engine.run(scenario, RunParameters.defaults(2024, 3600)), TRIPS_GENERATED);

        assertTrue(generated > 950 && generated < 1150, "Generados: " + generated);
    }

    @Test
    void periodosSolapados_esError() {
        DemandSpec demand = new DemandSpec("d1", "E", "X", 5, ArrivalMode.PERIODIC,
                List.of(new DemandPeriod(0, 100, 10), new DemandPeriod(50, 200, 10)));

        InvalidScenarioException ex = assertThrows(InvalidScenarioException.class,
                () -> engine.run(new Scenario(TestNetworks.straightRoad(), List.of(demand), List.of()),
                        RunParameters.defaults(1, 300)));

        assertTrue(ex.validation().hasCode("DEMAND_OVERLAPPING_PERIODS"));
    }

    @Test
    void periodoConFinAntesDelInicio_esError() {
        DemandSpec demand = new DemandSpec("d1", "E", "X", 5, ArrivalMode.PERIODIC,
                List.of(new DemandPeriod(100, 50, 10)));

        InvalidScenarioException ex = assertThrows(InvalidScenarioException.class,
                () -> engine.run(new Scenario(TestNetworks.straightRoad(), List.of(demand), List.of()),
                        RunParameters.defaults(1, 300)));

        assertTrue(ex.validation().hasCode("DEMAND_INVALID_PERIOD"));
    }

    @Test
    void giroProhibido_losVehiculosTomanElDesvio() {
        Network network = TestNetworks.builder()
                .node("E", NodeType.ENTRY)
                .node("A", NodeType.INTERSECTION)
                .node("B", NodeType.INTERSECTION)
                .node("X", NodeType.EXIT)
                .edge("e1", "E", "A", 100, 10)
                .edge("directa", "A", "X", 100, 10)
                .edge("ab", "A", "B", 100, 10)
                .edge("bx", "B", "X", 100, 10)
                .build();
        Scenario scenario = new Scenario(network,
                List.of(new DemandSpec("d1", "E", "X", 6, ArrivalMode.PERIODIC)), List.of(),
                List.of(new Movement("e1", "directa")));

        SimulationResult result = engine.run(scenario, RunParameters.defaults(1, 600));

        assertTrue(value(result, TRIPS_COMPLETED) > 0);
        assertEquals(0.0, result.metric(VEHICLES_DISCHARGED, "directa").orElseThrow().value());
        assertEquals(value(result, TRIPS_COMPLETED), result.metric(VEHICLES_DISCHARGED, "bx").orElseThrow().value());
    }

    private static double value(SimulationResult result, String name) {
        return result.metric(name).orElseThrow().value();
    }
}
