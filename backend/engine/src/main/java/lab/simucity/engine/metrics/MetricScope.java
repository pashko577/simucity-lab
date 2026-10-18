package lab.simucity.engine.metrics;

public enum MetricScope {
    /** Toda la red. */
    NETWORK,
    /** Una calle (arista). */
    SEGMENT,
    /** Cola de ingreso hacia una calle de entrada. */
    ENTRY
}
