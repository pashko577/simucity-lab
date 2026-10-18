package lab.simucity.engine.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import lab.simucity.engine.TestNetworks;
import lab.simucity.engine.network.Edge;
import lab.simucity.engine.network.Network;
import lab.simucity.engine.network.Node;
import lab.simucity.engine.network.NodeType;

class DijkstraRouterTest {

    private final DijkstraRouter router = new DijkstraRouter();

    @Test
    void vialibre_encuentraRutaConTiempoEsperado() {
        Optional<Route> route = router.findRoute(TestNetworks.straightRoad(), "E", "X");

        assertTrue(route.isPresent());
        assertEquals(List.of("e1", "e2"), route.get().edgeIds());
        assertEquals(30.0, route.get().costSeconds(), 1e-9);
    }

    @Test
    void viaCerrada_noPermiteElPaso() {
        Network network = TestNetworks.builder()
                .node("E", NodeType.ENTRY)
                .node("X", NodeType.EXIT)
                .edge(new Edge("e1", "E", "X", 100, 1, 10, 10, true))
                .build();

        assertTrue(router.findRoute(network, "E", "X").isEmpty());
    }

    @Test
    void viaCerrada_seUsaElDesvioSiExiste() {
        Network network = TestNetworks.builder()
                .node("E", NodeType.ENTRY)
                .node("D", NodeType.INTERSECTION)
                .node("X", NodeType.EXIT)
                .edge(new Edge("directa", "E", "X", 100, 1, 10, 10, true))
                .edge("d1", "E", "D", 100, 10)
                .edge("d2", "D", "X", 100, 10)
                .build();

        Route route = router.findRoute(network, "E", "X").orElseThrow();

        assertEquals(List.of("d1", "d2"), route.edgeIds());
        assertEquals(20.0, route.costSeconds(), 1e-9);
    }

    @Test
    void sentidoContrario_noEsTransitable() {
        Network network = TestNetworks.builder()
                .node("E", NodeType.ENTRY)
                .node("X", NodeType.EXIT)
                .edge("e1", "X", "E", 100, 10)
                .build();

        assertTrue(router.findRoute(network, "E", "X").isEmpty());
    }

    @Test
    void rutaMasCorta_noSiempreEsLaMasRapida() {
        // Corta: 100 m a 2 m/s = 50 s. Larga: 300 m a 15 m/s = 20 s.
        Network network = TestNetworks.builder()
                .node("E", NodeType.ENTRY)
                .node("X", NodeType.EXIT)
                .edge("corta", "E", "X", 100, 2)
                .edge("larga", "E", "X", 300, 15)
                .build();

        Route route = router.findRoute(network, "E", "X").orElseThrow();

        assertEquals(List.of("larga"), route.edgeIds());
        assertEquals(20.0, route.costSeconds(), 1e-9);
    }

    @Test
    void nodoInexistente_devuelveVacio() {
        assertTrue(router.findRoute(TestNetworks.straightRoad(), "E", "NO_EXISTE").isEmpty());
    }

    @Test
    void empateDeCostos_resultadoNoDependeDelOrdenDeCreacion() {
        // Dos caminos de igual costo: E→A→X y E→B→X.
        List<Node> nodes = new ArrayList<>(List.of(
                new Node("E", NodeType.ENTRY, 0, 0),
                new Node("A", NodeType.INTERSECTION, 0, 0),
                new Node("B", NodeType.INTERSECTION, 0, 0),
                new Node("X", NodeType.EXIT, 0, 0)));
        List<Edge> edges = new ArrayList<>(List.of(
                new Edge("ea", "E", "A", 100, 1, 10, 10, false),
                new Edge("eb", "E", "B", 100, 1, 10, 10, false),
                new Edge("ax", "A", "X", 100, 1, 10, 10, false),
                new Edge("bx", "B", "X", 100, 1, 10, 10, false)));

        Route reference = router.findRoute(new Network(nodes, edges), "E", "X").orElseThrow();

        for (int i = 0; i < 20; i++) {
            Collections.shuffle(nodes, new java.util.Random(i));
            Collections.shuffle(edges, new java.util.Random(i + 1000));
            Route route = router.findRoute(new Network(nodes, edges), "E", "X").orElseThrow();
            assertEquals(reference, route, "La ruta cambió al reordenar la entrada (iteración " + i + ")");
        }
    }
}
