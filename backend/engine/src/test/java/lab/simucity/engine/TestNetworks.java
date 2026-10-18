package lab.simucity.engine;

import java.util.ArrayList;
import java.util.List;

import lab.simucity.engine.network.Edge;
import lab.simucity.engine.network.Network;
import lab.simucity.engine.network.Node;
import lab.simucity.engine.network.NodeType;

/** Constructor fluido de redes pequeñas para pruebas. */
public final class TestNetworks {

    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    public static TestNetworks builder() {
        return new TestNetworks();
    }

    public TestNetworks node(String id, NodeType type) {
        nodes.add(new Node(id, type, 0, 0));
        return this;
    }

    public TestNetworks node(String id, NodeType type, double x, double y) {
        nodes.add(new Node(id, type, x, y));
        return this;
    }

    /** Calle abierta de 1 carril y capacidad 10. */
    public TestNetworks edge(String id, String from, String to, double lengthMeters, double speedMps) {
        edges.add(new Edge(id, from, to, lengthMeters, 1, speedMps, 10, false));
        return this;
    }

    public TestNetworks edge(Edge edge) {
        edges.add(edge);
        return this;
    }

    public Network build() {
        return new Network(nodes, edges);
    }

    /**
     * Cruce de 4 accesos con calles de doble sentido. Centro C en (0,0); vecinos N, S, E, W a 100 m.
     * Aristas: {@code nIn} (N→C), {@code nOut} (C→N), y lo mismo para s, e, w.
     */
    public static Network crossIntersection() {
        TestNetworks b = builder()
                .node("C", NodeType.INTERSECTION, 0, 0)
                .node("N", NodeType.CONNECTOR, 0, 100)
                .node("S", NodeType.CONNECTOR, 0, -100)
                .node("E", NodeType.CONNECTOR, 100, 0)
                .node("W", NodeType.CONNECTOR, -100, 0);
        for (String leg : new String[] {"N", "S", "E", "W"}) {
            String l = leg.toLowerCase();
            b.edge(l + "In", leg, "C", 100, 10).edge(l + "Out", "C", leg, 100, 10);
        }
        return b.build();
    }

    /** Red mínima: E --(100 m a 10 m/s)--> I --(200 m a 10 m/s)--> X. Tiempo esperado: 30 s. */
    public static Network straightRoad() {
        return builder()
                .node("E", NodeType.ENTRY)
                .node("I", NodeType.INTERSECTION)
                .node("X", NodeType.EXIT)
                .edge("e1", "E", "I", 100, 10)
                .edge("e2", "I", "X", 200, 10)
                .build();
    }
}
