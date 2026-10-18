package lab.simucity.engine.simulation;

import java.util.List;
import java.util.Objects;

/**
 * Plan semafórico de una intersección. Las fases se repiten en ciclo.
 * Una intersección sin plan no tiene semáforo: todos sus movimientos están autorizados.
 *
 * @param nodeId         intersección controlada
 * @param offsetSeconds  desfase del ciclo respecto al inicio de la simulación, en segundos
 * @param phases         fases en orden
 */
public record SignalPlan(String nodeId, double offsetSeconds, List<SignalPhase> phases) {

    public SignalPlan {
        Objects.requireNonNull(nodeId, "nodeId");
        phases = List.copyOf(phases);
    }
}
