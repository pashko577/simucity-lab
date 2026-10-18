package lab.simucity.engine.network;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Qué movimientos entrada→salida se permiten en las intersecciones.
 *
 * <p>Por defecto se permite todo giro entre calles conectadas, salvo el giro en U (volver al nodo de
 * donde se vino). Además se pueden prohibir movimientos concretos.
 */
public final class MovementRules {

    public static final MovementRules DEFAULT = new MovementRules(List.of());

    private final Set<Movement> prohibited;

    public MovementRules(Collection<Movement> prohibited) {
        this.prohibited = Set.copyOf(prohibited);
    }

    public boolean allows(Edge from, Edge to) {
        return from.toNodeId().equals(to.fromNodeId())
                && !isUTurn(from, to)
                && !prohibited.contains(new Movement(from.id(), to.id()));
    }

    public static boolean isUTurn(Edge from, Edge to) {
        return to.toNodeId().equals(from.fromNodeId());
    }
}
