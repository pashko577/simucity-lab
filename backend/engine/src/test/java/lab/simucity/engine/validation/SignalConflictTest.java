package lab.simucity.engine.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import lab.simucity.engine.TestNetworks;
import lab.simucity.engine.network.Movement;
import lab.simucity.engine.network.MovementRules;
import lab.simucity.engine.network.Network;
import lab.simucity.engine.network.Node;
import lab.simucity.engine.simulation.RunParameters;
import lab.simucity.engine.simulation.Scenario;
import lab.simucity.engine.simulation.SignalPhase;
import lab.simucity.engine.simulation.SignalPlan;
import lab.simucity.engine.simulation.SignalTemplates;

/** Cruce en "+" con circulación por la derecha. Ver {@link TestNetworks#crossIntersection()}. */
class SignalConflictTest {

    private final Network cross = TestNetworks.crossIntersection();
    private final Node center = cross.node("C").orElseThrow();

    private static final Movement S_THROUGH = new Movement("sIn", "nOut");
    private static final Movement N_THROUGH = new Movement("nIn", "sOut");
    private static final Movement W_THROUGH = new Movement("wIn", "eOut");
    private static final Movement S_LEFT = new Movement("sIn", "wOut");
    private static final Movement S_RIGHT = new Movement("sIn", "eOut");
    private static final Movement N_RIGHT = new Movement("nIn", "wOut");

    @Test
    void pasosRectosPerpendiculares_seCruzan() {
        assertTrue(MovementConflicts.conflict(cross, center, S_THROUGH, W_THROUGH));
    }

    @Test
    void pasosRectosOpuestos_sonCompatibles() {
        assertFalse(MovementConflicts.conflict(cross, center, S_THROUGH, N_THROUGH));
    }

    @Test
    void giroIzquierda_cruzaAlPasoRectoOpuesto() {
        assertTrue(MovementConflicts.conflict(cross, center, S_LEFT, N_THROUGH));
    }

    @Test
    void giroDerecha_noCruzaAlPasoRectoOpuesto() {
        assertFalse(MovementConflicts.conflict(cross, center, S_RIGHT, N_THROUGH));
    }

    @Test
    void mismaSalidaDesdeAccesosDistintos_convergenYSonIncompatibles() {
        assertTrue(MovementConflicts.conflict(cross, center, S_RIGHT, W_THROUGH));
    }

    @Test
    void mismoAcceso_siempreCompatible() {
        assertFalse(MovementConflicts.conflict(cross, center, S_THROUGH, S_LEFT));
        assertFalse(MovementConflicts.conflict(cross, center, S_THROUGH, S_RIGHT));
    }

    @Test
    void conflictoEsSimetrico() {
        List<Movement> all = List.of(S_THROUGH, N_THROUGH, W_THROUGH, S_LEFT, S_RIGHT, N_RIGHT);
        for (Movement a : all) {
            for (Movement b : all) {
                assertEquals(MovementConflicts.conflict(cross, center, a, b),
                        MovementConflicts.conflict(cross, center, b, a), a + " vs " + b);
            }
        }
    }

    @Test
    void faseConMovimientosQueSeCruzan_esError() {
        SignalPlan plan = new SignalPlan("C", 0, List.of(new SignalPhase(30, List.of(S_THROUGH, W_THROUGH))));

        ValidationResult result = validate(plan);

        assertTrue(result.hasCode("SIGNAL_CONFLICTING_MOVEMENTS"));
    }

    @Test
    void planDeDosFasesSinGirosIzquierda_esValido() {
        SignalPlan plan = new SignalPlan("C", 0, List.of(
                new SignalPhase(30, List.of(S_THROUGH, N_THROUGH, S_RIGHT, N_RIGHT)),
                new SignalPhase(4, List.of()),
                new SignalPhase(30, List.of(W_THROUGH, new Movement("eIn", "wOut"),
                        new Movement("wIn", "sOut"), new Movement("eIn", "nOut"))),
                new SignalPhase(4, List.of())));

        ValidationResult result = validate(plan);

        assertTrue(result.isValid(), () -> "Errores inesperados: " + result.errors());
    }

    @Test
    void plantillaPorAccesos_siempreEsValida() {
        SignalPlan plan = SignalTemplates.splitPhasing(cross, "C", MovementRules.DEFAULT, 20, 3);

        ValidationResult result = validate(plan);

        assertTrue(result.isValid(), () -> "Errores inesperados: " + result.errors());
        assertEquals(8, plan.phases().size(), "4 accesos × (verde + todo-rojo)");
        assertEquals(3, plan.phases().getFirst().greenMovements().size(), "Recto, izquierda y derecha; sin giro en U");
    }

    @Test
    void verdeParaGiroEnU_esError() {
        SignalPlan plan = new SignalPlan("C", 0, List.of(new SignalPhase(30, List.of(new Movement("sIn", "sOut")))));

        assertTrue(validate(plan).hasCode("SIGNAL_MOVEMENT_NOT_ALLOWED"));
    }

    @Test
    void verdeParaGiroProhibido_esError() {
        SignalPlan plan = new SignalPlan("C", 0, List.of(new SignalPhase(30, List.of(S_LEFT))));
        Scenario scenario = new Scenario(cross, List.of(), List.of(plan), List.of(S_LEFT));

        assertTrue(new ScenarioValidator().validate(scenario, RunParameters.defaults(1, 60))
                .hasCode("SIGNAL_MOVEMENT_NOT_ALLOWED"));
    }

    @Test
    void giroProhibidoQueNoConectaCalles_esError() {
        Scenario scenario = new Scenario(cross, List.of(), List.of(), List.of(new Movement("sIn", "nIn")));

        assertTrue(new ScenarioValidator().validate(scenario, RunParameters.defaults(1, 60))
                .hasCode("MOVEMENT_INVALID"));
    }

    private ValidationResult validate(SignalPlan plan) {
        return new ScenarioValidator().validate(new Scenario(cross, List.of(), List.of(plan)),
                RunParameters.defaults(1, 60));
    }
}
