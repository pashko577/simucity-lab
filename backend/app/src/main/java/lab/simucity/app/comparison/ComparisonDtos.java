package lab.simucity.app.comparison;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lab.simucity.engine.metrics.MetricScope;

public final class ComparisonDtos {

    private ComparisonDtos() {
    }

    public record ComparisonRequest(@NotNull UUID baseRunId, @NotNull UUID candidateRunId) {
    }

    public record RunRef(UUID id, UUID scenarioId, String scenarioName, long seed, String engineVersion,
            double durationSeconds) {
    }

    /**
     * Una condición de comparación justa (sección 9.2). {@code equal = false} no impide comparar, pero la
     * interfaz debe advertir que parte de la diferencia puede no deberse al cambio estudiado.
     */
    public record Condition(String code, String label, boolean equal, String baseValue, String candidateValue) {
    }

    public enum ChangeKind { ADDED, REMOVED, MODIFIED }

    public enum ElementType { NODE, EDGE, DEMAND, SIGNAL, TURN }

    public record FieldChange(String field, Object before, Object after) {
    }

    /** Qué cambió entre los dos escenarios (sección 5.3: resaltar los parámetros que cambiaron). */
    public record ScenarioChange(ElementType elementType, String elementId, ChangeKind kind,
            List<FieldChange> fields) {
    }

    public enum Assessment {
        IMPROVED,
        WORSENED,
        UNCHANGED,
        /** La métrica es descriptiva: cambiar no es mejor ni peor. */
        NEUTRAL,
        /** Falta el valor en alguna de las dos ejecuciones (p. ej. percentil 95 sin observaciones suficientes). */
        NOT_COMPARABLE
    }

    /**
     * @param difference     candidato − base, en la unidad de la métrica
     * @param percentChange  (candidato − base) / base × 100; {@code null} si la base es 0 (sección 9.3)
     */
    public record MetricComparison(String name, MetricScope scope, String scopeId, String unit, Double base,
            Double candidate, Double difference, Double percentChange, Assessment assessment) {
    }

    public record ComparisonResponse(
            RunRef base,
            RunRef candidate,
            boolean fair,
            List<Condition> conditions,
            List<ScenarioChange> changes,
            List<MetricComparison> network,
            List<MetricComparison> segments) {
    }
}
