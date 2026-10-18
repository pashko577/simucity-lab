package lab.simucity.app.comparison;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lab.simucity.app.comparison.ComparisonDtos.Assessment;
import lab.simucity.app.comparison.ComparisonDtos.StatComparison;
import lab.simucity.app.runs.RunDtos.MetricDto;
import lab.simucity.engine.metrics.Comparison;
import lab.simucity.engine.metrics.MetricNames;
import lab.simucity.engine.metrics.MetricNames.Direction;
import lab.simucity.engine.metrics.Statistics;
import lab.simucity.engine.metrics.Statistics.DifferenceInterval;

/**
 * Compara dos lotes métrica a métrica. Si ambos tienen exactamente las mismas semillas, compara semilla a semilla
 * (pareado: el azar de las llegadas es el mismo en ambos lados); si no, compara medias (Welch).
 * Solo declara mejora o empeoramiento si el intervalo de confianza del 95 % excluye el cero.
 */
final class BatchComparator {

    private static final double EPSILON = 1e-9;

    private BatchComparator() {
    }

    /** @param base, candidate  métricas de red de cada ejecución completada, por semilla */
    static boolean paired(Map<Long, List<MetricDto>> base, Map<Long, List<MetricDto>> candidate) {
        return base.size() >= 2 && base.keySet().equals(candidate.keySet());
    }

    static List<StatComparison> compare(Map<Long, List<MetricDto>> base, Map<Long, List<MetricDto>> candidate) {
        boolean paired = paired(base, candidate);
        Map<String, String> units = new LinkedHashMap<>();
        base.values().forEach(metrics -> metrics.forEach(m -> units.putIfAbsent(m.name(), m.unit())));

        List<StatComparison> result = new ArrayList<>();
        for (Map.Entry<String, String> metric : units.entrySet()) {
            String name = metric.getKey();
            List<Double> a = new ArrayList<>();
            List<Double> b = new ArrayList<>();
            if (paired) {
                for (Long seed : base.keySet()) {
                    Double va = value(base.get(seed), name);
                    Double vb = value(candidate.get(seed), name);
                    if (va != null && vb != null) {
                        a.add(va);
                        b.add(vb);
                    }
                }
            } else {
                base.values().forEach(metrics -> addIfPresent(a, value(metrics, name)));
                candidate.values().forEach(metrics -> addIfPresent(b, value(metrics, name)));
            }
            result.add(compareMetric(name, metric.getValue(), a, b, paired));
        }
        return result;
    }

    static StatComparison compareMetric(String name, String unit, List<Double> a, List<Double> b, boolean paired) {
        Optional<DifferenceInterval> interval = paired
                ? Statistics.pairedDifference(a, b)
                : Statistics.welchDifference(a, b);
        Double meanA = a.isEmpty() ? null : Statistics.mean(a);
        Double meanB = b.isEmpty() ? null : Statistics.mean(b);
        Double sdA = a.isEmpty() ? null : Statistics.standardDeviation(a);
        Double sdB = b.isEmpty() ? null : Statistics.standardDeviation(b);
        Double percent = meanA == null || meanB == null ? null
                : Comparison.percentReduction(meanA, meanB).stream().map(r -> -r).boxed().findFirst().orElse(null);

        if (interval.isEmpty()) {
            return new StatComparison(name, unit, Math.min(a.size(), b.size()), paired, meanA, meanB, sdA, sdB,
                    null, null, null, percent, Assessment.NOT_COMPARABLE);
        }
        DifferenceInterval ci = interval.get();
        return new StatComparison(name, unit, ci.n(), paired, meanA, meanB, sdA, sdB, ci.meanDifference(), ci.low(),
                ci.high(), percent, assess(name, ci));
    }

    private static Assessment assess(String name, DifferenceInterval ci) {
        if (Math.abs(ci.low()) < EPSILON && Math.abs(ci.high()) < EPSILON) {
            return Assessment.UNCHANGED;
        }
        Direction direction = MetricNames.direction(name);
        if (direction == Direction.NEUTRAL) {
            return Assessment.NEUTRAL;
        }
        if (!ci.significant()) {
            return Assessment.INCONCLUSIVE;
        }
        boolean lower = ci.meanDifference() < 0;
        return (direction == Direction.LOWER_IS_BETTER) == lower ? Assessment.IMPROVED : Assessment.WORSENED;
    }

    private static Double value(List<MetricDto> metrics, String name) {
        return metrics.stream().filter(m -> m.name().equals(name)).findFirst().map(MetricDto::value).orElse(null);
    }

    private static void addIfPresent(List<Double> list, Double value) {
        if (value != null) {
            list.add(value);
        }
    }
}
