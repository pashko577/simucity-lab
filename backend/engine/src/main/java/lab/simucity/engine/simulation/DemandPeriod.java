package lab.simucity.engine.simulation;

/**
 * Intervalo en que una demanda tiene una tasa distinta a la base (p. ej. hora punta).
 *
 * @param fromSeconds        inicio, en segundos simulados (incluido)
 * @param toSeconds          fin, en segundos simulados (excluido)
 * @param vehiclesPerMinute  tasa durante el intervalo, en veh/min
 */
public record DemandPeriod(double fromSeconds, double toSeconds, double vehiclesPerMinute) {
}
