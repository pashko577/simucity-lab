package lab.simucity.engine.simulation;

/** La ejecución se detuvo porque {@link RunControl#shouldStop()} devolvió {@code true}. */
public final class SimulationAbortedException extends RuntimeException {

    private final long tick;

    public SimulationAbortedException(long tick) {
        super("Simulación detenida en el tick " + tick);
        this.tick = tick;
    }

    public long tick() {
        return tick;
    }
}
