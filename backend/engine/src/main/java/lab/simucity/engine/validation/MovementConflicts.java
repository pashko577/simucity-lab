package lab.simucity.engine.validation;

import lab.simucity.engine.network.Edge;
import lab.simucity.engine.network.Movement;
import lab.simucity.engine.network.Network;
import lab.simucity.engine.network.Node;

/**
 * Decide si dos movimientos de una misma intersección son incompatibles (no pueden tener verde a la vez).
 *
 * <p>Modelo geométrico: cada calle que toca la intersección es un punto en un círculo alrededor del
 * nodo, en el ángulo hacia el nodo vecino. En una misma calle de doble sentido, el carril de entrada y
 * el de salida se separan levemente según circulación por la derecha. Cada movimiento es una cuerda
 * entre su punto de entrada y su punto de salida; dos movimientos se cruzan si sus cuerdas se cortan,
 * es decir, si sus extremos se intercalan en el círculo.
 *
 * <ul>
 *   <li>Mismo acceso (misma calle de entrada): compatibles.</li>
 *   <li>Misma salida desde accesos distintos: incompatibles (convergen).</li>
 *   <li>En otro caso: incompatibles si las cuerdas se cortan.</li>
 * </ul>
 *
 * <p>Supone coordenadas con el eje Y hacia arriba y circulación por la derecha (ver {@link Node}).
 * El MVP no modela giros permisivos con cesión de paso: cualquier cruce es un conflicto.
 */
final class MovementConflicts {

    /** Separación angular, en radianes, entre el carril de entrada y el de salida de una misma calle. */
    private static final double LANE_OFFSET = 1e-6;
    private static final double TWO_PI = 2 * Math.PI;

    private MovementConflicts() {
    }

    /** Requiere que ambos movimientos sean válidos (calles existentes que pasan por {@code node}). */
    static boolean conflict(Network network, Node node, Movement a, Movement b) {
        if (a.fromEdgeId().equals(b.fromEdgeId())) {
            return false;
        }
        if (a.toEdgeId().equals(b.toEdgeId())) {
            return true;
        }
        double aIn = inboundAngle(network, node, a.fromEdgeId());
        double aOut = outboundAngle(network, node, a.toEdgeId());
        double bIn = inboundAngle(network, node, b.fromEdgeId());
        double bOut = outboundAngle(network, node, b.toEdgeId());
        return strictlyBetween(bIn, aIn, aOut) != strictlyBetween(bOut, aIn, aOut);
    }

    private static double inboundAngle(Network network, Node node, String edgeId) {
        Edge edge = network.edge(edgeId).orElseThrow();
        return normalize(angleTo(node, network.node(edge.fromNodeId()).orElseThrow()) + LANE_OFFSET);
    }

    private static double outboundAngle(Network network, Node node, String edgeId) {
        Edge edge = network.edge(edgeId).orElseThrow();
        return normalize(angleTo(node, network.node(edge.toNodeId()).orElseThrow()) - LANE_OFFSET);
    }

    private static double angleTo(Node from, Node to) {
        return StrictMath.atan2(to.y() - from.y(), to.x() - from.x());
    }

    /** ¿Está {@code x} en el arco abierto que va de {@code start} a {@code end} en sentido antihorario? */
    private static boolean strictlyBetween(double x, double start, double end) {
        double toX = normalize(x - start);
        double toEnd = normalize(end - start);
        return toX > 0 && toX < toEnd;
    }

    private static double normalize(double angle) {
        double result = angle % TWO_PI;
        return result < 0 ? result + TWO_PI : result;
    }
}
