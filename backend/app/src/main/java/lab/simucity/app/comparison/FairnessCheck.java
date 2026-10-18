package lab.simucity.app.comparison;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import lab.simucity.app.comparison.ComparisonDtos.Condition;
import lab.simucity.app.scenarios.ScenarioDefinition;
import lab.simucity.engine.simulation.RunParameters;

/**
 * Condiciones de una comparación justa (sección 9.2): mantener iguales demanda, duración, semilla y versión
 * del motor. Si alguna difiere, parte de la diferencia observada puede no deberse al cambio estudiado.
 */
final class FairnessCheck {

    private FairnessCheck() {
    }

    record RunFacts(String engineVersion, RunParameters parameters, ScenarioDefinition definition) {
    }

    static List<Condition> conditions(RunFacts base, RunFacts candidate) {
        RunParameters b = base.parameters();
        RunParameters c = candidate.parameters();
        boolean sameDemand = ScenarioDiff.sameDemand(base.definition(), candidate.definition());
        return List.of(
                condition("SAME_ENGINE_VERSION", "Versión del motor", base.engineVersion(), candidate.engineVersion()),
                condition("SAME_SEED", "Semilla", b.seed(), c.seed()),
                condition("SAME_DEMAND", "Demanda (orígenes, destinos y tasas)",
                        "", "", sameDemand),
                condition("SAME_DURATION", "Duración simulada", seconds(b.durationSeconds()), seconds(c.durationSeconds())),
                condition("SAME_TIME_STEP", "Paso temporal", seconds(b.dtSeconds()), seconds(c.dtSeconds())),
                condition("SAME_SATURATION_FLOW", "Flujo de saturación",
                        b.saturationFlowPerLaneVph() + " veh/h", c.saturationFlowPerLaneVph() + " veh/h"));
    }

    static boolean isFair(List<Condition> conditions) {
        return conditions.stream().allMatch(Condition::equal);
    }

    private static Condition condition(String code, String label, Object base, Object candidate) {
        return condition(code, label, String.valueOf(base), String.valueOf(candidate), Objects.equals(base, candidate));
    }

    private static Condition condition(String code, String label, String base, String candidate, boolean equal) {
        return new Condition(code, label, equal, base, candidate);
    }

    private static String seconds(double value) {
        return String.format(Locale.ROOT, "%s s", value);
    }
}
