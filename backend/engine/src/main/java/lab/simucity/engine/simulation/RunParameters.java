package lab.simucity.engine.simulation;

/**
 * Parámetros de una ejecución. Se guardan completos con cada resultado para poder repetirla.
 *
 * @param seed                      semilla pseudoaleatoria
 * @param dtSeconds                 paso temporal simulado, en segundos
 * @param durationSeconds           duración simulada, en segundos
 * @param sampleIntervalSeconds     cada cuánto se guarda una muestra del estado, en segundos
 * @param saturationFlowPerLaneVph  vehículos por hora y carril que puede descargar una cola (parámetro pedagógico)
 * @param stallThresholdSeconds     tiempo sin ninguna descarga, con cola presente, para reportar un bloqueo
 */
public record RunParameters(
        long seed,
        double dtSeconds,
        double durationSeconds,
        double sampleIntervalSeconds,
        double saturationFlowPerLaneVph,
        double stallThresholdSeconds) {

    public static final double DEFAULT_DT_SECONDS = 0.5;
    public static final double DEFAULT_SAMPLE_INTERVAL_SECONDS = 5.0;
    public static final double DEFAULT_SATURATION_FLOW_VPH = 1800.0;
    public static final double DEFAULT_STALL_THRESHOLD_SECONDS = 300.0;

    public static RunParameters defaults(long seed, double durationSeconds) {
        return new RunParameters(seed, DEFAULT_DT_SECONDS, durationSeconds, DEFAULT_SAMPLE_INTERVAL_SECONDS,
                DEFAULT_SATURATION_FLOW_VPH, DEFAULT_STALL_THRESHOLD_SECONDS);
    }

    public RunParameters withSampleIntervalSeconds(double value) {
        return new RunParameters(seed, dtSeconds, durationSeconds, value, saturationFlowPerLaneVph, stallThresholdSeconds);
    }

    public RunParameters withSeed(long value) {
        return new RunParameters(value, dtSeconds, durationSeconds, sampleIntervalSeconds, saturationFlowPerLaneVph,
                stallThresholdSeconds);
    }

    public long durationTicks() {
        return Math.round(durationSeconds / dtSeconds);
    }

    public long sampleEveryTicks() {
        return Math.max(1, Math.round(sampleIntervalSeconds / dtSeconds));
    }

    public long stallTicks() {
        return Math.max(1, Math.round(stallThresholdSeconds / dtSeconds));
    }
}
