package lab.simucity.engine.metrics;

/** Nombres estables de métricas. La interfaz y la base de datos dependen de ellos: no renombrar. */
public final class MetricNames {

    // Red
    public static final String TRIPS_GENERATED = "trips_generated";
    public static final String TRIPS_COMPLETED = "trips_completed";
    public static final String TRIPS_INCOMPLETE = "trips_incomplete";
    /** Desde que el vehículo se genera (incluye espera en la entrada) hasta que sale de la red. */
    public static final String TRAVEL_TIME_MEAN = "travel_time_mean";
    /** Percentil 95 por rango más cercano; no aplica con menos de {@link #MIN_OBSERVATIONS_P95} viajes. */
    public static final String TRAVEL_TIME_P95 = "travel_time_p95";
    /** Tiempo en que el vehículo podía avanzar pero estuvo detenido (cola, semáforo, calle llena o entrada). */
    public static final String WAIT_TIME_MEAN = "wait_time_mean";
    public static final String THROUGHPUT = "throughput";
    /** Promedio en el tiempo de vehículos en cola en toda la red, incluidas las entradas. */
    public static final String QUEUE_MEAN = "queue_mean";

    // Calle o entrada
    public static final String QUEUE_MAX = "queue_max";
    public static final String VEHICLES_DISCHARGED = "vehicles_discharged";
    /** Vehículos descargados / capacidad de descarga en la duración. Aproximación pedagógica. */
    public static final String UTILIZATION = "utilization";

    public static final int MIN_OBSERVATIONS_P95 = 20;

    private MetricNames() {
    }
}
