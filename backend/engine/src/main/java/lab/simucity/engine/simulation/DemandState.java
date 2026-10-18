package lab.simucity.engine.simulation;

import java.util.List;
import java.util.Random;

/**
 * Generador de llegadas de una demanda.
 *
 * <p>Cada demanda tiene su propio {@link Random} derivado de la semilla y de su ID. Así, agregar otra
 * demanda o cambiar un semáforo no altera la secuencia de llegadas de esta: los escenarios
 * comparados reciben exactamente los mismos vehículos. {@link Random} está especificado por la JLS
 * y produce la misma secuencia en cualquier JVM.
 */
final class DemandState {

    final DemandSpec spec;
    final List<QueueSource> path;
    final QueueSource entry;
    private final double dtSeconds;
    private final Random rng;

    DemandState(DemandSpec spec, List<QueueSource> path, QueueSource entry, long seed, double dtSeconds) {
        this.spec = spec;
        this.path = path;
        this.entry = entry;
        this.dtSeconds = dtSeconds;
        this.rng = new Random(streamSeed(seed, spec.id()));
    }

    int arrivals(long tick) {
        double before = spec.expectedVehiclesUntil(tick * dtSeconds);
        double after = spec.expectedVehiclesUntil((tick + 1) * dtSeconds);
        return switch (spec.mode()) {
            case PERIODIC -> (int) ((long) Math.floor(after) - (long) Math.floor(before));
            case POISSON -> poisson(after - before);
        };
    }

    /**
     * Algoritmo de Knuth; adecuado para las tasas pequeñas por tick que admite el validador.
     * Con tasa 0 no consume números aleatorios.
     */
    private int poisson(double mean) {
        if (mean <= 0) {
            return 0;
        }
        double limit = StrictMath.exp(-mean);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= rng.nextDouble();
        } while (p > limit);
        return k - 1;
    }

    /** Mezcla SplitMix64 de la semilla con el ID; String.hashCode está especificado y es estable. */
    static long streamSeed(long seed, String demandId) {
        long z = seed + 0x9E3779B97F4A7C15L * (demandId.hashCode() + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
