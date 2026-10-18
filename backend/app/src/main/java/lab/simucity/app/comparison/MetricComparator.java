package lab.simucity.app.comparison;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lab.simucity.app.comparison.ComparisonDtos.Assessment;
import lab.simucity.app.comparison.ComparisonDtos.MetricComparison;
import lab.simucity.app.runs.RunDtos.MetricDto;
import lab.simucity.engine.metrics.Comparison;
import lab.simucity.engine.metrics.MetricNames;
import lab.simucity.engine.metrics.MetricNames.Direction;

/** Empareja las métricas de dos ejecuciones y evalúa cada variación. Sin estado ni dependencias. */
final class MetricComparator {

    private static final double EPSILON = 1e-9;

    private MetricComparator() {
    }

    /**
     * Compara las métricas presentes en ambas ejecuciones, en el orden de la base. Las que solo existen en una
     * (p. ej. una calle eliminada) se omiten: no hay con qué compararlas.
     */
    static List<MetricComparison> compare(List<MetricDto> base, List<MetricDto> candidate) {
        Map<String, MetricDto> candidateByKey = new LinkedHashMap<>();
        candidate.forEach(m -> candidateByKey.put(key(m), m));

        List<MetricComparison> result = new ArrayList<>();
        for (MetricDto b : base) {
            MetricDto c = candidateByKey.get(key(b));
            if (c != null) {
                result.add(compare(b, c));
            }
        }
        return result;
    }

    static MetricComparison compare(MetricDto base, MetricDto candidate) {
        Double difference = null;
        Double percentChange = null;
        Assessment assessment;

        if (base.value() == null || candidate.value() == null) {
            assessment = Assessment.NOT_COMPARABLE;
        } else {
            double b = base.value();
            double c = candidate.value();
            difference = c - b;
            // Sección 9.3: se reutiliza la reducción porcentual del motor (A − B) / A, con signo invertido.
            percentChange = Comparison.percentReduction(b, c).stream().map(r -> -r).boxed().findFirst().orElse(null);
            assessment = assess(base.name(), difference);
        }
        return new MetricComparison(base.name(), base.scope(), base.scopeId(), base.unit(), base.value(),
                candidate.value(), difference, percentChange, assessment);
    }

    private static Assessment assess(String metricName, double difference) {
        if (Math.abs(difference) < EPSILON) {
            return Assessment.UNCHANGED;
        }
        Direction direction = MetricNames.direction(metricName);
        return switch (direction) {
            case NEUTRAL -> Assessment.NEUTRAL;
            case LOWER_IS_BETTER -> difference < 0 ? Assessment.IMPROVED : Assessment.WORSENED;
            case HIGHER_IS_BETTER -> difference > 0 ? Assessment.IMPROVED : Assessment.WORSENED;
        };
    }

    private static String key(MetricDto m) {
        return m.scope() + "|" + m.scopeId() + "|" + m.name();
    }
}
