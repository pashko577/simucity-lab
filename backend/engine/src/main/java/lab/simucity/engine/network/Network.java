package lab.simucity.engine.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Red vial inmutable como grafo dirigido.
 *
 * <p>Las listas de nodos y aristas se conservan tal como llegaron (para que el validador pueda
 * detectar duplicados). Las consultas de adyacencia devuelven aristas ordenadas por ID, de modo
 * que ningún algoritmo dependa del orden en que el usuario creó los elementos.
 */
public final class Network {

    private final List<Node> nodes;
    private final List<Edge> edges;
    private final Map<String, Node> nodesById;
    private final Map<String, Edge> edgesById;
    private final Map<String, List<Edge>> outgoingByNode;
    private final Map<String, List<Edge>> incomingByNode;

    public Network(List<Node> nodes, List<Edge> edges) {
        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);

        Map<String, Node> byId = new LinkedHashMap<>();
        for (Node node : this.nodes) {
            byId.putIfAbsent(node.id(), node);
        }
        this.nodesById = byId;

        Map<String, Edge> edgeLookup = new LinkedHashMap<>();
        for (Edge edge : this.edges) {
            edgeLookup.putIfAbsent(edge.id(), edge);
        }
        this.edgesById = edgeLookup;

        Map<String, List<Edge>> outgoing = new LinkedHashMap<>();
        Map<String, List<Edge>> incoming = new LinkedHashMap<>();
        List<Edge> sortedEdges = new ArrayList<>(this.edges);
        sortedEdges.sort(Comparator.comparing(Edge::id));
        for (Edge edge : sortedEdges) {
            outgoing.computeIfAbsent(edge.fromNodeId(), k -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.toNodeId(), k -> new ArrayList<>()).add(edge);
        }
        outgoing.replaceAll((k, v) -> List.copyOf(v));
        incoming.replaceAll((k, v) -> List.copyOf(v));
        this.outgoingByNode = outgoing;
        this.incomingByNode = incoming;
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<Edge> edges() {
        return edges;
    }

    public Optional<Node> node(String id) {
        return Optional.ofNullable(nodesById.get(id));
    }

    public Optional<Edge> edge(String id) {
        return Optional.ofNullable(edgesById.get(id));
    }

    /** Aristas que salen del nodo, ordenadas por ID. */
    public List<Edge> outgoing(String nodeId) {
        return outgoingByNode.getOrDefault(nodeId, List.of());
    }

    /** Aristas que llegan al nodo, ordenadas por ID. */
    public List<Edge> incoming(String nodeId) {
        return incomingByNode.getOrDefault(nodeId, List.of());
    }

    /** Nodos del tipo indicado, ordenados por ID. */
    public List<Node> nodesOfType(NodeType type) {
        return nodesById.values().stream()
                .filter(n -> n.type() == type)
                .sorted(Comparator.comparing(Node::id))
                .toList();
    }
}
