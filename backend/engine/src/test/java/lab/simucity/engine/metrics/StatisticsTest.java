package lab.simucity.engine.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import lab.simucity.engine.metrics.Statistics.DifferenceInterval;
import lab.simucity.engine.metrics.Statistics.Summary;

/** Valores esperados calculados a mano (y comprobables con cualquier hoja de cálculo). */
class StatisticsTest {

    @Test
    void resumen_mediaDesviacionMinimoMaximo() {
        Summary s = Statistics.summarize(List.of(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0));

        assertEquals(8, s.n());
        assertEquals(5.0, s.mean(), 1e-12);
        assertEquals(2.138089935, s.standardDeviation(), 1e-9); // muestral: sqrt(32 / 7)
        assertEquals(2.0, s.min());
        assertEquals(9.0, s.max());
    }

    @Test
    void desviacionConUnSoloValor_esCero() {
        assertEquals(0.0, Statistics.standardDeviation(List.of(3.0)));
    }

    @Test
    void pareado_diferenciaClaraEsSignificativa() {
        // Diferencias: -10, -12, -11, -9, -13 → media -11, sd 1,5811, t(4) = 2,776 → ±1,963
        DifferenceInterval ci = Statistics.pairedDifference(
                List.of(50.0, 52.0, 51.0, 49.0, 53.0), List.of(40.0, 40.0, 40.0, 40.0, 40.0)).orElseThrow();

        assertEquals(-11.0, ci.meanDifference(), 1e-12);
        assertEquals(-12.963, ci.low(), 1e-3);
        assertEquals(-9.037, ci.high(), 1e-3);
        assertTrue(ci.paired());
        assertTrue(ci.significant());
    }

    @Test
    void pareado_diferenciaDentroDelRuido_noEsSignificativa() {
        DifferenceInterval ci = Statistics.pairedDifference(
                List.of(50.0, 52.0, 51.0), List.of(51.0, 50.0, 52.5)).orElseThrow();

        assertFalse(ci.significant());
        assertTrue(ci.low() < 0 && ci.high() > 0);
    }

    @Test
    void pareado_diferenciaConstante_intervaloDeAnchoCeroYSignificativa() {
        DifferenceInterval ci = Statistics.pairedDifference(List.of(10.0, 20.0), List.of(13.0, 23.0)).orElseThrow();

        assertEquals(3.0, ci.low(), 1e-12);
        assertEquals(3.0, ci.high(), 1e-12);
        assertTrue(ci.significant());
    }

    @Test
    void pareado_sinCambios_noEsSignificativo() {
        DifferenceInterval ci = Statistics.pairedDifference(List.of(1.0, 2.0), List.of(1.0, 2.0)).orElseThrow();

        assertFalse(ci.significant());
    }

    @Test
    void conMenosDeDosValores_noHayIntervalo() {
        assertTrue(Statistics.pairedDifference(List.of(1.0), List.of(2.0)).isEmpty());
        assertTrue(Statistics.welchDifference(List.of(1.0), List.of(2.0, 3.0)).isEmpty());
    }

    @Test
    void welch_lotesConSemillasDistintas() {
        // A: media 10, var 2,5 (n=5) · B: media 14, var 2,5 (n=5) → se = 1, df = 8, t = 2,306
        DifferenceInterval ci = Statistics.welchDifference(
                List.of(8.0, 9.0, 10.0, 11.0, 12.0), List.of(12.0, 13.0, 14.0, 15.0, 16.0)).orElseThrow();

        assertEquals(4.0, ci.meanDifference(), 1e-12);
        assertEquals(4.0 - 2.306, ci.low(), 1e-9);
        assertEquals(4.0 + 2.306, ci.high(), 1e-9);
        assertFalse(ci.paired());
        assertTrue(ci.significant());
    }

    @Test
    void valoresCriticosT() {
        assertEquals(12.706, Statistics.tCritical95(1));
        assertEquals(2.262, Statistics.tCritical95(9));
        assertEquals(1.960, Statistics.tCritical95(1000));
        assertThrows(IllegalArgumentException.class, () -> Statistics.tCritical95(0));
    }
}
