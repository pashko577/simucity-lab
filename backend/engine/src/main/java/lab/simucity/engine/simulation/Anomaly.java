package lab.simucity.engine.simulation;

/**
 * Situación sospechosa detectada durante la ejecución (p. ej. un bloqueo total).
 * Una cola que avanza lentamente NO es una anomalía: es un resultado válido del escenario.
 *
 * @param code       código estable ({@code EDGE_STALLED}, {@code ENTRY_STALLED})
 * @param message    explicación en lenguaje sencillo
 * @param elementId  arista afectada
 * @param atSeconds  tiempo simulado en que empezó el problema
 */
public record Anomaly(String code, String message, String elementId, double atSeconds) {
}
