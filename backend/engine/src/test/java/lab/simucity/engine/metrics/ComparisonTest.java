package lab.simucity.engine.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import lab.simucity.engine.metrics.MetricNames.Direction;

class ComparisonTest {

    @Test
    void reduccionPorcentual_segunLaSeccion9_3() {
        assertEquals(25.0, Comparison.percentReduction(40, 30).orElseThrow(), 1e-9);
        assertEquals(-50.0, Comparison.percentReduction(20, 30).orElseThrow(), 1e-9);
    }

    @Test
    void reduccionPorcentual_baseCeroONoFinitaNoAplica() {
        assertFalse(Comparison.percentReduction(0, 5).isPresent());
        assertFalse(Comparison.percentReduction(Double.NaN, 5).isPresent());
    }

    @Test
    void direccionDeCadaMetrica() {
        assertEquals(Direction.LOWER_IS_BETTER, MetricNames.direction(MetricNames.TRAVEL_TIME_MEAN));
        assertEquals(Direction.LOWER_IS_BETTER, MetricNames.direction(MetricNames.TRIPS_INCOMPLETE));
        assertEquals(Direction.HIGHER_IS_BETTER, MetricNames.direction(MetricNames.THROUGHPUT));
        assertEquals(Direction.NEUTRAL, MetricNames.direction(MetricNames.TRIPS_GENERATED));
        assertEquals(Direction.NEUTRAL, MetricNames.direction("metrica_desconocida"));
    }
}
