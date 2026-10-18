package lab.simucity.app.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import lab.simucity.app.comparison.ComparisonDtos.Assessment;
import lab.simucity.app.comparison.ComparisonDtos.ChangeKind;
import lab.simucity.app.comparison.ComparisonDtos.Condition;
import lab.simucity.app.comparison.ComparisonDtos.ElementType;
import lab.simucity.app.comparison.ComparisonDtos.FieldChange;
import lab.simucity.app.comparison.ComparisonDtos.MetricComparison;
import lab.simucity.app.comparison.ComparisonDtos.ScenarioChange;
import lab.simucity.app.comparison.FairnessCheck.RunFacts;
import lab.simucity.app.runs.RunDtos.MetricDto;
import lab.simucity.app.scenarios.ScenarioDefinition;
import lab.simucity.app.scenarios.ScenarioDefinition.DemandDef;
import lab.simucity.app.scenarios.ScenarioDefinition.EdgeDef;
import lab.simucity.app.scenarios.ScenarioDefinition.MovementDef;
import lab.simucity.app.scenarios.ScenarioDefinition.NodeDef;
import lab.simucity.app.scenarios.ScenarioDefinition.PhaseDef;
import lab.simucity.app.scenarios.ScenarioDefinition.SignalDef;
import lab.simucity.engine.metrics.MetricScope;
import lab.simucity.engine.network.NodeType;
import lab.simucity.engine.simulation.ArrivalMode;
import lab.simucity.engine.simulation.RunParameters;

/** Lógica pura de la comparación: sin Spring ni base de datos. */
class ComparisonLogicTest {

    // --- Métricas ------------------------------------------------------------------------------------

    private static MetricDto network(String name, Double value) {
        return new MetricDto(name, value, "s", MetricScope.NETWORK, null, 10);
    }

    @Test
    void menosTiempoDeViaje_esMejora_conPorcentajeSegunLaSeccion9_3() {
        MetricComparison c = MetricComparator.compare(network("travel_time_mean", 40.0), network("travel_time_mean", 30.0));

        assertThat(c.difference()).isEqualTo(-10.0);
        assertThat(c.percentChange()).isEqualTo(-25.0);
        assertThat(c.assessment()).isEqualTo(Assessment.IMPROVED);
    }

    @Test
    void menosViajesCompletados_esEmpeoramiento() {
        MetricComparison c = MetricComparator.compare(network("trips_completed", 100.0), network("trips_completed", 80.0));

        assertThat(c.assessment()).isEqualTo(Assessment.WORSENED);
        assertThat(c.percentChange()).isEqualTo(-20.0);
    }

    @Test
    void baseCero_sinPorcentajePeroConDiferenciaAbsoluta() {
        MetricComparison c = MetricComparator.compare(network("queue_mean", 0.0), network("queue_mean", 3.0));

        assertThat(c.percentChange()).isNull();
        assertThat(c.difference()).isEqualTo(3.0);
        assertThat(c.assessment()).isEqualTo(Assessment.WORSENED);
    }

    @Test
    void valorAusente_noComparable_eIgual_sinCambios_yDescriptiva_neutral() {
        assertThat(MetricComparator.compare(network("travel_time_p95", null), network("travel_time_p95", 50.0))
                .assessment()).isEqualTo(Assessment.NOT_COMPARABLE);
        assertThat(MetricComparator.compare(network("wait_time_mean", 5.0), network("wait_time_mean", 5.0))
                .assessment()).isEqualTo(Assessment.UNCHANGED);
        assertThat(MetricComparator.compare(network("trips_generated", 100.0), network("trips_generated", 101.0))
                .assessment()).isEqualTo(Assessment.NEUTRAL);
    }

    @Test
    void soloSeComparanLasMetricasPresentesEnAmbas() {
        List<MetricDto> base = List.of(segment("queue_max", "e1", 4.0), segment("queue_max", "borrada", 9.0));
        List<MetricDto> candidate = List.of(segment("queue_max", "e1", 2.0), segment("queue_max", "nueva", 1.0));

        List<MetricComparison> result = MetricComparator.compare(base, candidate);

        assertThat(result).extracting(MetricComparison::scopeId).containsExactly("e1");
    }

    private static MetricDto segment(String name, String edgeId, Double value) {
        return new MetricDto(name, value, "veh", MetricScope.SEGMENT, edgeId, 10);
    }

    // --- Diferencias entre escenarios ------------------------------------------------------------------

    private static ScenarioDefinition road(int lanes, boolean closed, double rate) {
        return new ScenarioDefinition(
                List.of(new NodeDef("E", NodeType.ENTRY, 0.0, 0.0), new NodeDef("X", NodeType.EXIT, 100.0, 0.0)),
                List.of(new EdgeDef("e1", "E", "X", 100.0, lanes, 10.0, 15, closed)),
                List.of(new DemandDef("d1", "E", "X", rate, ArrivalMode.POISSON, null)),
                List.of(),
                List.of());
    }

    @Test
    void escenariosIguales_sinCambios_aunqueSeMuevanLosNodos() {
        ScenarioDefinition base = road(1, false, 10);
        ScenarioDefinition moved = new ScenarioDefinition(
                List.of(new NodeDef("E", NodeType.ENTRY, 50.0, 50.0), new NodeDef("X", NodeType.EXIT, 100.0, 0.0)),
                base.edges(), base.demands(), base.signals(), base.prohibitedMovements());

        assertThat(ScenarioDiff.between(base, moved)).isEmpty();
    }

    @Test
    void detectaCamposModificadosDeUnaCalle() {
        List<ScenarioChange> changes = ScenarioDiff.between(road(1, false, 10), road(2, true, 10));

        assertThat(changes).hasSize(1);
        ScenarioChange change = changes.getFirst();
        assertThat(change.elementType()).isEqualTo(ElementType.EDGE);
        assertThat(change.kind()).isEqualTo(ChangeKind.MODIFIED);
        assertThat(change.fields()).containsExactly(
                new FieldChange("lanes", 1, 2), new FieldChange("closed", false, true));
    }

    @Test
    void detectaAltasBajasSemaforosYGirosProhibidos() {
        ScenarioDefinition base = road(1, false, 10);
        List<EdgeDef> edges = new ArrayList<>(base.edges());
        edges.add(new EdgeDef("e2", "X", "E", 100.0, 1, 10.0, 15, false));
        ScenarioDefinition candidate = new ScenarioDefinition(base.nodes(), edges, List.of(),
                List.of(new SignalDef("X", 0.0, List.of(new PhaseDef(30.0, List.of(new MovementDef("e1", "e2")))))),
                List.of(new MovementDef("e1", "e2")));

        List<ScenarioChange> changes = ScenarioDiff.between(base, candidate);

        assertThat(changes).extracting(c -> c.elementType() + ":" + c.elementId() + ":" + c.kind()).containsExactly(
                "EDGE:e2:ADDED", "DEMAND:d1:REMOVED", "SIGNAL:X:ADDED", "TURN:e1→e2:ADDED");
    }

    // --- Comparación justa -----------------------------------------------------------------------------

    @Test
    void mismasCondiciones_esJusta() {
        RunFacts a = new RunFacts("0.2.0", RunParameters.defaults(42, 600), road(1, false, 10));
        RunFacts b = new RunFacts("0.2.0", RunParameters.defaults(42, 600), road(2, false, 10));

        List<Condition> conditions = FairnessCheck.conditions(a, b);

        assertThat(FairnessCheck.isFair(conditions)).isTrue();
    }

    @Test
    void semillaODemandaDistintas_noEsJustaYDiceQue() {
        RunFacts a = new RunFacts("0.2.0", RunParameters.defaults(42, 600), road(1, false, 10));
        RunFacts b = new RunFacts("0.2.0", RunParameters.defaults(7, 600), road(1, false, 20));

        List<Condition> conditions = FairnessCheck.conditions(a, b);

        assertThat(FairnessCheck.isFair(conditions)).isFalse();
        assertThat(conditions).filteredOn(c -> !c.equal()).extracting(Condition::code)
                .containsExactlyInAnyOrder("SAME_SEED", "SAME_DEMAND");
    }
}
