package lab.simucity.engine.validation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lab.simucity.engine.network.Edge;
import lab.simucity.engine.network.Network;
import lab.simucity.engine.network.Node;
import lab.simucity.engine.network.NodeType;

/**
 * Valida la estructura y los valores de una red antes de simular.
 * Nunca lanza excepciones por datos inválidos: devuelve errores y advertencias.
 */
public final class NetworkValidator {

    /** Límites de tamaño para evitar cargas excesivas. */
    public record Limits(int maxNodes, int maxEdges) {
        public static final Limits MVP_DEFAULT = new Limits(500, 2_000);
    }

    private final Limits limits;

    public NetworkValidator() {
        this(Limits.MVP_DEFAULT);
    }

    public NetworkValidator(Limits limits) {
        this.limits = limits;
    }

    public ValidationResult validate(Network network) {
        List<ValidationIssue> issues = new ArrayList<>();

        if (network.nodes().isEmpty()) {
            issues.add(error("NETWORK_EMPTY", "La red no tiene nodos.", null));
            return new ValidationResult(issues);
        }
        if (network.nodes().size() > limits.maxNodes()) {
            issues.add(error("NETWORK_TOO_MANY_NODES",
                    "La red tiene " + network.nodes().size() + " nodos; el máximo es " + limits.maxNodes() + ".", null));
        }
        if (network.edges().size() > limits.maxEdges()) {
            issues.add(error("NETWORK_TOO_MANY_EDGES",
                    "La red tiene " + network.edges().size() + " calles; el máximo es " + limits.maxEdges() + ".", null));
        }

        checkDuplicateIds(network, issues);
        for (Edge edge : network.edges()) {
            checkEdge(network, edge, issues);
        }
        if (!issues.stream().allMatch(i -> i.severity() == Severity.WARNING)) {
            // Con errores estructurales, el análisis de conectividad no es fiable.
            return new ValidationResult(issues);
        }

        checkConnectivity(network, issues);
        checkEntriesAndExits(network, issues);
        return new ValidationResult(issues);
    }

    private static void checkDuplicateIds(Network network, List<ValidationIssue> issues) {
        Set<String> seen = new HashSet<>();
        for (Node node : network.nodes()) {
            if (!seen.add(node.id())) {
                issues.add(error("NODE_DUPLICATE_ID", "Hay más de un nodo con el ID '" + node.id() + "'.", node.id()));
            }
        }
        seen.clear();
        for (Edge edge : network.edges()) {
            if (!seen.add(edge.id())) {
                issues.add(error("EDGE_DUPLICATE_ID", "Hay más de una calle con el ID '" + edge.id() + "'.", edge.id()));
            }
        }
    }

    private static void checkEdge(Network network, Edge edge, List<ValidationIssue> issues) {
        String id = edge.id();
        if (network.node(edge.fromNodeId()).isEmpty()) {
            issues.add(error("EDGE_UNKNOWN_FROM_NODE",
                    "La calle '" + id + "' sale de un nodo inexistente ('" + edge.fromNodeId() + "').", id));
        }
        if (network.node(edge.toNodeId()).isEmpty()) {
            issues.add(error("EDGE_UNKNOWN_TO_NODE",
                    "La calle '" + id + "' llega a un nodo inexistente ('" + edge.toNodeId() + "').", id));
        }
        if (edge.fromNodeId().equals(edge.toNodeId())) {
            issues.add(error("EDGE_SELF_LOOP", "La calle '" + id + "' empieza y termina en el mismo nodo.", id));
        }
        if (!isPositiveFinite(edge.lengthMeters())) {
            issues.add(error("EDGE_INVALID_LENGTH",
                    "La longitud de la calle '" + id + "' debe ser un número mayor que 0 m.", id));
        }
        if (!isPositiveFinite(edge.speedLimitMps())) {
            issues.add(error("EDGE_INVALID_SPEED",
                    "La velocidad de la calle '" + id + "' debe ser un número mayor que 0 m/s.", id));
        }
        if (edge.lanes() < 1) {
            issues.add(error("EDGE_INVALID_LANES", "La calle '" + id + "' debe tener al menos 1 carril.", id));
        }
        if (edge.storageCapacity() < 1) {
            issues.add(error("EDGE_INVALID_CAPACITY",
                    "La capacidad de la calle '" + id + "' debe ser de al menos 1 vehículo.", id));
        }
    }

    /** Advierte si la red tiene partes aisladas (conectividad débil, ignorando sentidos). */
    private static void checkConnectivity(Network network, List<ValidationIssue> issues) {
        String start = network.nodes().getFirst().id();
        Set<String> visited = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.push(start);
        visited.add(start);
        while (!pending.isEmpty()) {
            String current = pending.pop();
            for (Edge e : network.outgoing(current)) {
                if (visited.add(e.toNodeId())) {
                    pending.push(e.toNodeId());
                }
            }
            for (Edge e : network.incoming(current)) {
                if (visited.add(e.fromNodeId())) {
                    pending.push(e.fromNodeId());
                }
            }
        }
        for (Node node : network.nodes()) {
            if (!visited.contains(node.id())) {
                issues.add(warning("NETWORK_DISCONNECTED",
                        "El nodo '" + node.id() + "' no está conectado con el resto de la red.", node.id()));
            }
        }
    }

    /** Advierte de entradas sin salida alcanzable y salidas que ninguna entrada alcanza (por vías abiertas). */
    private static void checkEntriesAndExits(Network network, List<ValidationIssue> issues) {
        List<Node> entries = network.nodesOfType(NodeType.ENTRY);
        List<Node> exits = network.nodesOfType(NodeType.EXIT);
        if (entries.isEmpty()) {
            issues.add(warning("NETWORK_NO_ENTRY", "La red no tiene nodos de entrada.", null));
        }
        if (exits.isEmpty()) {
            issues.add(warning("NETWORK_NO_EXIT", "La red no tiene nodos de salida.", null));
        }
        if (entries.isEmpty() || exits.isEmpty()) {
            return;
        }

        Set<String> exitIds = new HashSet<>();
        exits.forEach(n -> exitIds.add(n.id()));
        Set<String> reachedExits = new HashSet<>();

        for (Node entry : entries) {
            Set<String> reachable = reachableByOpenEdges(network, entry.id());
            boolean anyExit = false;
            for (String id : reachable) {
                if (exitIds.contains(id)) {
                    reachedExits.add(id);
                    anyExit = true;
                }
            }
            if (!anyExit) {
                issues.add(warning("ENTRY_NO_REACHABLE_EXIT",
                        "Desde la entrada '" + entry.id() + "' no se puede llegar a ninguna salida.", entry.id()));
            }
        }
        for (Node exit : exits) {
            if (!reachedExits.contains(exit.id())) {
                issues.add(warning("EXIT_UNREACHABLE",
                        "Ninguna entrada puede llegar a la salida '" + exit.id() + "'.", exit.id()));
            }
        }
    }

    private static Set<String> reachableByOpenEdges(Network network, String from) {
        Set<String> visited = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.push(from);
        visited.add(from);
        while (!pending.isEmpty()) {
            String current = pending.pop();
            for (Edge e : network.outgoing(current)) {
                if (!e.closed() && visited.add(e.toNodeId())) {
                    pending.push(e.toNodeId());
                }
            }
        }
        return visited;
    }

    private static boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private static ValidationIssue error(String code, String message, String elementId) {
        return new ValidationIssue(Severity.ERROR, code, message, elementId);
    }

    private static ValidationIssue warning(String code, String message, String elementId) {
        return new ValidationIssue(Severity.WARNING, code, message, elementId);
    }
}
