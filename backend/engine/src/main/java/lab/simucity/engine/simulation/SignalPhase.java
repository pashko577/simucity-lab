package lab.simucity.engine.simulation;

import java.util.List;

import lab.simucity.engine.network.Movement;

/**
 * Fase de un semáforo.
 *
 * @param durationSeconds  duración de la fase, en segundos
 * @param greenMovements   movimientos autorizados; vacío = todo en rojo (despeje)
 */
public record SignalPhase(double durationSeconds, List<Movement> greenMovements) {

    public SignalPhase {
        greenMovements = List.copyOf(greenMovements);
    }
}
