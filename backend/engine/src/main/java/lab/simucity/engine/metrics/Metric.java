package lab.simucity.engine.metrics;

import java.util.Objects;

/**
 * Valor calculado con su definición mínima.
 *
 * @param name          nombre estable (ver {@link MetricNames})
 * @param value         valor; {@code null} si no aplica (p. ej. sin observaciones suficientes)
 * @param unit          unidad legible
 * @param scope         alcance
 * @param scopeId       ID de la calle/entrada; {@code null} para la red
 * @param observations  cantidad de observaciones usadas
 */
public record Metric(String name, Double value, String unit, MetricScope scope, String scopeId, long observations) {

    public Metric {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(scope, "scope");
    }

    public boolean isApplicable() {
        return value != null;
    }
}
