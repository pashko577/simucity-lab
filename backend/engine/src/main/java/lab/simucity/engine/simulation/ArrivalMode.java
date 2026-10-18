package lab.simucity.engine.simulation;

public enum ArrivalMode {
    /** Llegadas a intervalos fijos según la tasa. */
    PERIODIC,
    /** Llegadas aleatorias (Poisson) reproducibles con la semilla. */
    POISSON
}
