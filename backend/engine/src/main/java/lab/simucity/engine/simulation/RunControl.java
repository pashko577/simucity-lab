package lab.simucity.engine.simulation;

/**
 * Permite a quien ejecuta el motor detenerlo (cancelación del usuario o presupuesto de tiempo agotado).
 * El motor lo consulta cada {@link #CHECK_EVERY_TICKS} ticks; no afecta a los resultados.
 */
@FunctionalInterface
public interface RunControl {

    int CHECK_EVERY_TICKS = 256;

    RunControl NONE = () -> false;

    /** {@code true} para abortar la ejecución con {@link SimulationAbortedException}. */
    boolean shouldStop();
}
