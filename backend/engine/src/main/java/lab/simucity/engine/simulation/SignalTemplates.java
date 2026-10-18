package lab.simucity.engine.simulation;

import java.util.ArrayList;
import java.util.List;

import lab.simucity.engine.network.Edge;
import lab.simucity.engine.network.Movement;
import lab.simucity.engine.network.MovementRules;
import lab.simucity.engine.network.Network;

/**
 * Plantillas de planes semafóricos que siempre pasan la validación de conflictos.
 * Son el punto de partida recomendado en el editor (sección 8.3 de la especificación).
 */
public final class SignalTemplates {

    private SignalTemplates() {
    }

    /**
     * Una fase por acceso: cada calle de entrada recibe verde para todos sus giros permitidos, por turnos,
     * con una fase de todo-rojo entre accesos. Nunca hay conflictos porque en cada fase solo avanza un acceso.
     *
     * @param greenSeconds   duración del verde de cada acceso
     * @param allRedSeconds  despeje entre accesos; 0 para omitirlo
     */
    public static SignalPlan splitPhasing(Network network, String nodeId, MovementRules rules,
            double greenSeconds, double allRedSeconds) {
        List<SignalPhase> phases = new ArrayList<>();
        for (Edge in : network.incoming(nodeId)) {
            List<Movement> movements = new ArrayList<>();
            for (Edge out : network.outgoing(nodeId)) {
                if (rules.allows(in, out)) {
                    movements.add(new Movement(in.id(), out.id()));
                }
            }
            if (movements.isEmpty()) {
                continue;
            }
            phases.add(new SignalPhase(greenSeconds, movements));
            if (allRedSeconds > 0) {
                phases.add(new SignalPhase(allRedSeconds, List.of()));
            }
        }
        return new SignalPlan(nodeId, 0, phases);
    }
}
