package lab.simucity.engine.simulation;

import java.util.List;
import java.util.Optional;

import lab.simucity.engine.metrics.Metric;
import lab.simucity.engine.metrics.MetricScope;
import lab.simucity.engine.validation.ValidationIssue;

/**
 * Resultado completo de una ejecución. Dos ejecuciones con el mismo escenario, parámetros y versión
 * del motor producen resultados {@code equals}.
 */
public record SimulationResult(
        String engineVersion,
        RunParameters parameters,
        long durationTicks,
        List<Metric> metrics,
        List<Sample> samples,
        List<Anomaly> anomalies,
        List<ValidationIssue> warnings) {

    public SimulationResult {
        metrics = List.copyOf(metrics);
        samples = List.copyOf(samples);
        anomalies = List.copyOf(anomalies);
        warnings = List.copyOf(warnings);
    }

    /** Métrica de alcance red. */
    public Optional<Metric> metric(String name) {
        return metrics.stream()
                .filter(m -> m.scope() == MetricScope.NETWORK && m.name().equals(name))
                .findFirst();
    }

    /** Métrica de una calle o entrada concreta. */
    public Optional<Metric> metric(String name, String scopeId) {
        return metrics.stream()
                .filter(m -> m.name().equals(name) && scopeId.equals(m.scopeId()))
                .findFirst();
    }
}
