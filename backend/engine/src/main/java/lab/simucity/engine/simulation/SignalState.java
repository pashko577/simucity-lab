package lab.simucity.engine.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import lab.simucity.engine.network.Movement;

/** Estado en ejecución de un plan semafórico. La fase se calcula desde el tick, sin acumular tiempo. */
final class SignalState {

    final String nodeId;
    private final long cycleTicks;
    private final long offsetTicks;
    private final long[] phaseEndTicks;
    private final List<Set<Movement>> greens = new ArrayList<>();
    private Set<Movement> current = Set.of();

    SignalState(SignalPlan plan, double dtSeconds) {
        this.nodeId = plan.nodeId();
        this.offsetTicks = Math.round(plan.offsetSeconds() / dtSeconds);
        this.phaseEndTicks = new long[plan.phases().size()];
        long cumulative = 0;
        for (int i = 0; i < plan.phases().size(); i++) {
            SignalPhase phase = plan.phases().get(i);
            cumulative += Math.max(1, Math.round(phase.durationSeconds() / dtSeconds));
            phaseEndTicks[i] = cumulative;
            greens.add(Set.copyOf(phase.greenMovements()));
        }
        this.cycleTicks = cumulative;
    }

    void update(long tick) {
        long position = Math.floorMod(tick + offsetTicks, cycleTicks);
        for (int i = 0; i < phaseEndTicks.length; i++) {
            if (position < phaseEndTicks[i]) {
                current = greens.get(i);
                return;
            }
        }
    }

    boolean allows(String fromEdgeId, String toEdgeId) {
        return current.contains(new Movement(fromEdgeId, toEdgeId));
    }
}
