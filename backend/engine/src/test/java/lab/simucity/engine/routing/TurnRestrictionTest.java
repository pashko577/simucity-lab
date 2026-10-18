package lab.simucity.engine.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import lab.simucity.engine.TestNetworks;
import lab.simucity.engine.network.Movement;
import lab.simucity.engine.network.MovementRules;
import lab.simucity.engine.network.Network;
import lab.simucity.engine.network.NodeType;

class TurnRestrictionTest {

    private final DijkstraRouter router = new DijkstraRouter();

    @Test
    void giroProhibido_laRutaUsaElDesvio() {
        // E → A; desde A: directa a X (100 m) o desvío A → B → X (200 m).
        Network network = TestNetworks.builder()
                .node("E", NodeType.ENTRY)
                .node("A", NodeType.INTERSECTION)
                .node("B", NodeType.INTERSECTION)
                .node("X", NodeType.EXIT)
                .edge("e1", "E", "A", 100, 10)
                .edge("directa", "A", "X", 100, 10)
                .edge("ab", "A", "B", 100, 10)
                .edge("bx", "B", "X", 100, 10)
                .build();
        MovementRules rules = new MovementRules(List.of(new Movement("e1", "directa")));

        assertEquals(List.of("e1", "directa"), router.findRoute(network, "E", "X").orElseThrow().edgeIds());
        assertEquals(List.of("e1", "ab", "bx"), router.findRoute(network, "E", "X", rules).orElseThrow().edgeIds());
    }

    @Test
    void giroEnU_noSePermiteNiSiquieraComoUnicaSalida() {
        // S → M. En M está prohibido girar a X directamente; la única alternativa sería
        // ir a A y volver (giro en U en A), que tampoco se permite.
        Network network = TestNetworks.builder()
                .node("S", NodeType.ENTRY)
                .node("M", NodeType.INTERSECTION)
                .node("A", NodeType.INTERSECTION)
                .node("X", NodeType.EXIT)
                .edge("s1", "S", "M", 100, 10)
                .edge("ma", "M", "A", 100, 10)
                .edge("am", "A", "M", 100, 10)
                .edge("mx", "M", "X", 100, 10)
                .build();
        MovementRules rules = new MovementRules(List.of(new Movement("s1", "mx")));

        assertTrue(router.findRoute(network, "S", "X", rules).isEmpty());
    }

    @Test
    void reglasPorDefecto_permitenGirosYProhibenGiroEnU() {
        Network cross = TestNetworks.crossIntersection();
        MovementRules rules = MovementRules.DEFAULT;

        assertTrue(rules.allows(cross.edge("sIn").orElseThrow(), cross.edge("nOut").orElseThrow()));
        assertTrue(rules.allows(cross.edge("sIn").orElseThrow(), cross.edge("wOut").orElseThrow()));
        assertFalse(rules.allows(cross.edge("sIn").orElseThrow(), cross.edge("sOut").orElseThrow()));
        assertFalse(rules.allows(cross.edge("sIn").orElseThrow(), cross.edge("sIn").orElseThrow()),
                "Calles que no se tocan no forman un movimiento");
    }
}
