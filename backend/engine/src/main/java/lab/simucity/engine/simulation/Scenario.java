package lab.simucity.engine.simulation;

import java.util.List;
import java.util.Objects;

import lab.simucity.engine.network.Movement;
import lab.simucity.engine.network.MovementRules;
import lab.simucity.engine.network.Network;

/**
 * Red + demanda + semáforos + restricciones de giro: todo lo que define un experimento,
 * salvo los parámetros de ejecución.
 *
 * @param prohibitedMovements  giros prohibidos además del giro en U (que siempre está prohibido)
 */
public record Scenario(
        Network network,
        List<DemandSpec> demands,
        List<SignalPlan> signals,
        List<Movement> prohibitedMovements) {

    public Scenario {
        Objects.requireNonNull(network, "network");
        demands = List.copyOf(demands);
        signals = List.copyOf(signals);
        prohibitedMovements = List.copyOf(prohibitedMovements);
    }

    public Scenario(Network network, List<DemandSpec> demands, List<SignalPlan> signals) {
        this(network, demands, signals, List.of());
    }

    public MovementRules movementRules() {
        return new MovementRules(prohibitedMovements);
    }
}
