package lab.simucity.engine.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import lab.simucity.engine.TestNetworks;
import lab.simucity.engine.network.Edge;
import lab.simucity.engine.network.Network;
import lab.simucity.engine.network.Node;
import lab.simucity.engine.network.NodeType;

class NetworkValidatorTest {

    private final NetworkValidator validator = new NetworkValidator();

    @Test
    void redCorrecta_sinProblemas() {
        ValidationResult result = validator.validate(TestNetworks.straightRoad());

        assertTrue(result.isValid());
        assertTrue(result.issues().isEmpty(), () -> "Problemas inesperados: " + result.issues());
    }

    @Test
    void redVacia_esError() {
        ValidationResult result = validator.validate(new Network(List.of(), List.of()));

        assertFalse(result.isValid());
        assertTrue(result.hasCode("NETWORK_EMPTY"));
    }

    @Test
    void redDesconectada_produceAdvertenciaPeroEsEjecutable() {
        Network network = TestNetworks.builder()
                .node("E", NodeType.ENTRY)
                .node("X", NodeType.EXIT)
                .node("AISLADO", NodeType.INTERSECTION)
                .edge("e1", "E", "X", 100, 10)
                .build();

        ValidationResult result = validator.validate(network);

        assertTrue(result.isValid());
        assertTrue(result.hasCode("NETWORK_DISCONNECTED"));
    }

    @Test
    void salidaInalcanzablePorViaCerrada_produceAdvertencia() {
        Network network = TestNetworks.builder()
                .node("E", NodeType.ENTRY)
                .node("X", NodeType.EXIT)
                .edge(new Edge("e1", "E", "X", 100, 1, 10, 10, true))
                .build();

        ValidationResult result = validator.validate(network);

        assertTrue(result.hasCode("ENTRY_NO_REACHABLE_EXIT"));
        assertTrue(result.hasCode("EXIT_UNREACHABLE"));
    }

    @Test
    void calleHaciaNodoInexistente_esError() {
        Network network = TestNetworks.builder()
                .node("E", NodeType.ENTRY)
                .edge("e1", "E", "FANTASMA", 100, 10)
                .build();

        ValidationResult result = validator.validate(network);

        assertFalse(result.isValid());
        assertTrue(result.hasCode("EDGE_UNKNOWN_TO_NODE"));
    }

    @Test
    void valoresInvalidos_sonErrores() {
        Network network = TestNetworks.builder()
                .node("E", NodeType.ENTRY)
                .node("X", NodeType.EXIT)
                .edge(new Edge("negativa", "E", "X", -5, 1, 10, 10, false))
                .edge(new Edge("nan", "E", "X", Double.NaN, 1, 10, 10, false))
                .edge(new Edge("infinita", "E", "X", 100, 1, Double.POSITIVE_INFINITY, 10, false))
                .edge(new Edge("sinCarriles", "E", "X", 100, 0, 10, 10, false))
                .edge(new Edge("sinCapacidad", "E", "X", 100, 1, 10, 0, false))
                .build();

        ValidationResult result = validator.validate(network);

        assertFalse(result.isValid());
        assertTrue(result.hasCode("EDGE_INVALID_LENGTH"));
        assertTrue(result.hasCode("EDGE_INVALID_SPEED"));
        assertTrue(result.hasCode("EDGE_INVALID_LANES"));
        assertTrue(result.hasCode("EDGE_INVALID_CAPACITY"));
    }

    @Test
    void idsDuplicados_sonError() {
        Network network = new Network(
                List.of(new Node("A", NodeType.ENTRY, 0, 0), new Node("A", NodeType.EXIT, 0, 0)),
                List.of());

        ValidationResult result = validator.validate(network);

        assertFalse(result.isValid());
        assertTrue(result.hasCode("NODE_DUPLICATE_ID"));
    }

    @Test
    void bucleEnElMismoNodo_esError() {
        Network network = TestNetworks.builder()
                .node("A", NodeType.INTERSECTION)
                .edge("loop", "A", "A", 100, 10)
                .build();

        assertTrue(validator.validate(network).hasCode("EDGE_SELF_LOOP"));
    }

    @Test
    void redSobreElLimite_esError() {
        NetworkValidator strict = new NetworkValidator(new NetworkValidator.Limits(2, 10));

        ValidationResult result = strict.validate(TestNetworks.straightRoad());

        assertFalse(result.isValid());
        assertTrue(result.hasCode("NETWORK_TOO_MANY_NODES"));
    }
}
