package lab.simucity.engine.metrics;

import java.util.List;
import java.util.Optional;

/**
 * Estadística para comparar escenarios con varias semillas (secciones 9.2 y 20.4): media, desviación típica e
 * intervalo de confianza del 95 % para la diferencia de medias, con la distribución t de Student.
 */
public final class Statistics {

    /** Valores críticos t de dos colas al 95 % para 1..30 grados de libertad. */
    private static final double[] T_95 = {
            12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
            2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086,
            2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042};

    private Statistics() {
    }

    public record Summary(int n, double mean, double standardDeviation, double min, double max) {
    }

    /**
     * Intervalo de confianza del 95 % para la diferencia (candidato − base).
     *
     * @param paired  {@code true} si cada valor del candidato se emparejó con el de la base de su misma semilla
     */
    public record DifferenceInterval(double meanDifference, double low, double high, int n, boolean paired) {

        /** El intervalo no contiene el cero: la diferencia no se explica por el azar de las semillas. */
        public boolean significant() {
            return low > 0 || high < 0;
        }
    }

    public static Summary summarize(List<Double> values) {
        int n = values.size();
        double mean = mean(values);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
        return new Summary(n, mean, standardDeviation(values), min, max);
    }

    public static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    /** Desviación típica muestral (n − 1). Con menos de 2 valores es 0. */
    public static double standardDeviation(List<Double> values) {
        int n = values.size();
        if (n < 2) {
            return 0.0;
        }
        double mean = mean(values);
        double sumSquares = 0;
        for (double v : values) {
            sumSquares += (v - mean) * (v - mean);
        }
        return Math.sqrt(sumSquares / (n - 1));
    }

    /**
     * Comparación pareada: mismas semillas en ambos lados, así que se analiza la diferencia de cada par. Es más
     * sensible que comparar medias porque el azar de las llegadas es el mismo en ambos escenarios.
     *
     * @return vacío con menos de 2 pares
     */
    public static Optional<DifferenceInterval> pairedDifference(List<Double> base, List<Double> candidate) {
        if (base.size() != candidate.size()) {
            throw new IllegalArgumentException("Las listas emparejadas deben tener el mismo tamaño");
        }
        int n = base.size();
        if (n < 2) {
            return Optional.empty();
        }
        List<Double> differences = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            differences.add(candidate.get(i) - base.get(i));
        }
        double mean = mean(differences);
        double halfWidth = tCritical95(n - 1) * standardDeviation(differences) / Math.sqrt(n);
        return Optional.of(new DifferenceInterval(mean, mean - halfWidth, mean + halfWidth, n, true));
    }

    /**
     * Comparación de medias con varianzas distintas (Welch), para lotes con semillas diferentes.
     * Los grados de libertad fraccionarios se redondean hacia abajo (criterio conservador).
     *
     * @return vacío si algún lado tiene menos de 2 valores
     */
    public static Optional<DifferenceInterval> welchDifference(List<Double> base, List<Double> candidate) {
        int nA = base.size();
        int nB = candidate.size();
        if (nA < 2 || nB < 2) {
            return Optional.empty();
        }
        double varA = Math.pow(standardDeviation(base), 2) / nA;
        double varB = Math.pow(standardDeviation(candidate), 2) / nB;
        double meanDifference = mean(candidate) - mean(base);
        double se = Math.sqrt(varA + varB);
        if (se == 0) {
            return Optional.of(new DifferenceInterval(meanDifference, meanDifference, meanDifference,
                    Math.min(nA, nB), false));
        }
        double df = Math.pow(varA + varB, 2)
                / (Math.pow(varA, 2) / (nA - 1) + Math.pow(varB, 2) / (nB - 1));
        double halfWidth = tCritical95((int) Math.floor(df)) * se;
        return Optional.of(new DifferenceInterval(meanDifference, meanDifference - halfWidth,
                meanDifference + halfWidth, Math.min(nA, nB), false));
    }

    /** Valor crítico t de dos colas al 95 %. Por encima de 30 grados de libertad, aproximaciones tabuladas. */
    public static double tCritical95(int degreesOfFreedom) {
        if (degreesOfFreedom < 1) {
            throw new IllegalArgumentException("Se necesita al menos 1 grado de libertad");
        }
        if (degreesOfFreedom <= 30) {
            return T_95[degreesOfFreedom - 1];
        }
        if (degreesOfFreedom <= 40) {
            return 2.021;
        }
        if (degreesOfFreedom <= 60) {
            return 2.000;
        }
        if (degreesOfFreedom <= 120) {
            return 1.980;
        }
        return 1.960;
    }
}
