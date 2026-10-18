package lab.simucity.app.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AutoSampleIntervalTest {

    private static final long LIMIT = 300_000;

    @Test
    void redPequena_usaElIntervaloMasFino() {
        assertThat(SimulationConfig.autoSampleInterval(10, 3600, LIMIT)).isEqualTo(5.0);
    }

    @Test
    void redMaximaDosHoras_eligeElPrimeroQueCabe() {
        // 1560 calles × (7200/30 + 1) = 375 960 > límite; × (7200/60 + 1) = 188 760 ≤ límite.
        assertThat(SimulationConfig.autoSampleInterval(1560, 7200, LIMIT)).isEqualTo(60.0);
    }

    @Test
    void siempreDevuelveUnValor_inclusoEnCasosExtremos() {
        assertThat(SimulationConfig.autoSampleInterval(1_000_000, 7200, LIMIT)).isEqualTo(600.0);
    }
}
