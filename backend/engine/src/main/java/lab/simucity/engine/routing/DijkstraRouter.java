package lab.simucity.engine.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

import lab.simucity.engine.network.Edge;
import lab.simucity.engine.network.MovementRules;
import lab.simucity.engine.network.Network;

/**
 * Ruta de menor tiempo de recorrido a flujo libre (longitud / velocidad), ignorando vías cerradas
 * y respetando las restricciones de giro.
 *
 * <p>La búsqueda se hace sobre aristas, no sobre nodos: llegar a un nodo por calles distintas permite
 * giros distintos, así que el estado de la búsqueda es "estoy al final de esta calle".
 *
 * <p>Determinista: ante costos iguales desempata por ID de arista, y la red entrega las aristas
 * ordenadas por ID, así que el resultado no depende del orden de creación de los elementos.
 * Los mapas internos solo se consultan por clave, nunca se iteran.
 */
public final class DijkstraRouter {

    private record QueueEntry(Edge edge, double cost) {
    }

    private static final Comparator<QueueEntry> ORDER = Comparator.comparingDouble(QueueEntry::cost)
            .thenComparing(entry -> entry.edge().id());

    /** Igual que {@link #findRoute(Network, String, String, MovementRules)} con las reglas por defecto. */
    public Optional<Route> findRoute(Network network, String fromNodeId, String toNodeId) {
        return findRoute(network, fromNodeId, toNodeId, MovementRules.DEFAULT);
    }

    /** @return la ruta, o vacío si el origen o destino no existen o no hay camino abierto y permitido. */
    public Optional<Route> findRoute(Network network, String fromNodeId, String toNodeId, MovementRules rules) {
        if (network.node(fromNodeId).isEmpty() || network.node(toNodeId).isEmpty()) {
            return Optional.empty();
        }
        if (fromNodeId.equals(toNodeId)) {
            return Optional.of(new Route(List.of(), 0.0));
        }

        Map<String, Double> best = new HashMap<>();
        Map<String, Edge> previous = new HashMap<>();
        Set<String> settled = new HashSet<>();
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(ORDER);

        for (Edge edge : network.outgoing(fromNodeId)) {
            if (!edge.closed()) {
                double cost = edge.freeFlowTravelTimeSeconds();
                best.put(edge.id(), cost);
                queue.add(new QueueEntry(edge, cost));
            }
        }

        while (!queue.isEmpty()) {
            QueueEntry current = queue.poll();
            Edge edge = current.edge();
            if (!settled.add(edge.id())) {
                continue;
            }
            if (edge.toNodeId().equals(toNodeId)) {
                return Optional.of(buildRoute(previous, edge, current.cost()));
            }
            for (Edge next : network.outgoing(edge.toNodeId())) {
                if (next.closed() || settled.contains(next.id()) || !rules.allows(edge, next)) {
                    continue;
                }
                double candidate = current.cost() + next.freeFlowTravelTimeSeconds();
                Double known = best.get(next.id());
                if (known == null || candidate < known) {
                    best.put(next.id(), candidate);
                    previous.put(next.id(), edge);
                    queue.add(new QueueEntry(next, candidate));
                }
            }
        }
        return Optional.empty();
    }

    private static Route buildRoute(Map<String, Edge> previous, Edge last, double cost) {
        List<String> edgeIds = new ArrayList<>();
        for (Edge cursor = last; cursor != null; cursor = previous.get(cursor.id())) {
            edgeIds.add(cursor.id());
        }
        Collections.reverse(edgeIds);
        return new Route(edgeIds, cost);
    }
}
