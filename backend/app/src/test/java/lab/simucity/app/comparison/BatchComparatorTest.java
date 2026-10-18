package lab.simucity.app.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import lab.simucity.app.comparison.ComparisonDtos.Assessment;
import lab.simucity.app.comparison.ComparisonDtos.StatComparison;
import lab.simucity.app.runs.RunDtos.MetricDto;
import lab.simucity.engine.metrics.MetricScope;

class BatchComparatorTest {

    /** Lote con una métrica: semilla → valor. */
    private static Map<Long, List<MetricDto>> batch(String metric, Object... seedValue) {
        Map<Long, List<MetricDto>> map = new LinkedHashMap<>();
        for (int i = 0; i < seedValue.length; i += 2) {
            Double value = (Double) seedValue[i + 1];
            map.put(((Number) seedValue[i]).longValue(),
                    List.of(new MetricDto(metric, value, "s", MetricScope.NETWORK, null, 10)));
        }
        return map;
    }

    @Test
    void mismasSemillas_esPareada_yUnaReduccionClaraEsMejora() {
        var a = batch("travel_time_mean", 1, 50.0, 2, 52.0, 3, 51.0, 4, 49.0, 5, 53.0);
        var b = batch("travel_time_mean", 1, 40.0, 2, 40.0, 3, 40.0, 4, 40.0, 5, 40.0);

        StatComparison c = BatchComparator.compare(a, b).getFirst();

        assertThat(BatchComparator.paired(a, b)).isTrue();
        assertThat(c.paired()).isTrue();
        assertThat(c.n()).isEqualTo(5);
        assertThat(c.meanDifference()).isEqualTo(-11.0);
        assertThat(c.ciHigh()).isLessThan(0);
        assertThat(c.percentChange()).isCloseTo(-21.57, org.assertj.core.data.Offset.offset(0.01));
        assertThat(c.assessment()).isEqualTo(Assessment.IMPROVED);
    }

    @Test
    void diferenciaDentroDelRuido_noConcluyente() {
        var a = batch("wait_time_mean", 1, 10.0, 2, 14.0, 3, 9.0);
        var b = batch("wait_time_mean", 1, 11.0, 2, 12.0, 3, 10.5);

        assertThat(BatchComparator.compare(a, b).getFirst().assessment()).isEqualTo(Assessment.INCONCLUSIVE);
    }

    @Test
    void resultadosIdenticos_sinCambios() {
        var a = batch("queue_mean", 1, 3.0, 2, 4.0);

        assertThat(BatchComparator.compare(a, a).getFirst().assessment()).isEqualTo(Assessment.UNCHANGED);
    }

    @Test
    void semillasDistintas_seComparanMedias_sinEmparejar() {
        var a = batch("travel_time_mean", 1, 30.0, 2, 31.0, 3, 29.0);
        var b = batch("travel_time_mean", 7, 60.0, 8, 61.0, 9, 59.0);

        StatComparison c = BatchComparator.compare(a, b).getFirst();

        assertThat(c.paired()).isFalse();
        assertThat(c.assessment()).isEqualTo(Assessment.WORSENED);
    }

    @Test
    void sinSuficientesValores_noComparable_yDescriptiva_neutral() {
        var a = batch("travel_time_p95", 1, null, 2, null);
        var b = batch("travel_time_p95", 1, 40.0, 2, 41.0);
        assertThat(BatchComparator.compare(a, b).getFirst().assessment()).isEqualTo(Assessment.NOT_COMPARABLE);

        var c = batch("trips_generated", 1, 100.0, 2, 110.0);
        var d = batch("trips_generated", 1, 120.0, 2, 135.0);
        assertThat(BatchComparator.compare(c, d).getFirst().assessment()).isEqualTo(Assessment.NEUTRAL);
    }
}
