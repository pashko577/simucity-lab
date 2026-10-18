package lab.simucity.engine.metrics;

import java.util.OptionalDouble;

/** Utilidades para comparar escenarios. */
public final class Comparison {

    private Comparison() {
    }

    /**
     * Reducción porcentual para una métrica donde menor es mejor: {@code (A − B) / A × 100}.
     * Positivo = el escenario nuevo mejora.
     *
     * @param base       valor del escenario base (A)
     * @param candidate  valor del escenario nuevo (B)
     * @return vacío si {@code A == 0} o algún valor no es finito; en ese caso usar la diferencia absoluta
     */
    public static OptionalDouble percentReduction(double base, double candidate) {
        if (base == 0 || !Double.isFinite(base) || !Double.isFinite(candidate)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((base - candidate) / base * 100.0);
    }
}
