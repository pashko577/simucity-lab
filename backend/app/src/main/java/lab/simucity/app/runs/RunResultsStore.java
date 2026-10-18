package lab.simucity.app.runs;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import lab.simucity.app.runs.RunDtos.MetricDto;
import lab.simucity.engine.metrics.Metric;
import lab.simucity.engine.metrics.MetricScope;

/**
 * Métricas y muestras de una ejecución. Se usa JDBC directo: son miles de filas de solo lectura que se
 * insertan en lote una vez, sin necesidad de entidades JPA.
 */
@Repository
class RunResultsStore {

    private final JdbcTemplate jdbc;
    private final JdbcClient client;

    RunResultsStore(JdbcTemplate jdbc, JdbcClient client) {
        this.jdbc = jdbc;
        this.client = client;
    }

    void saveMetrics(UUID runId, List<Metric> metrics) {
        jdbc.batchUpdate("""
                INSERT INTO simulation_metrics
                    (run_id, position, metric_name, metric_value, unit, scope_type, scope_id, observations)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, IntStream.range(0, metrics.size()).boxed().toList(), 500, (PreparedStatement ps, Integer i) -> {
            Metric m = metrics.get(i);
            ps.setObject(1, runId);
            ps.setInt(2, i);
            ps.setString(3, m.name());
            if (m.value() == null) {
                ps.setNull(4, Types.DOUBLE);
            } else {
                ps.setDouble(4, m.value());
            }
            ps.setString(5, m.unit());
            ps.setString(6, m.scope().name());
            ps.setString(7, m.scopeId());
            ps.setLong(8, m.observations());
        });
    }

    /** Métricas en el orden en que las produjo el motor; filtra por alcance si se indica. */
    List<MetricDto> findMetrics(UUID runId, MetricScope scope) {
        return client.sql("""
                        SELECT metric_name, metric_value, unit, scope_type, scope_id, observations
                        FROM simulation_metrics
                        WHERE run_id = :runId AND (CAST(:scope AS VARCHAR) IS NULL OR scope_type = :scope)
                        ORDER BY position
                        """)
                .param("runId", runId)
                .param("scope", scope == null ? null : scope.name(), Types.VARCHAR)
                .query((rs, i) -> new MetricDto(
                        rs.getString("metric_name"),
                        rs.getObject("metric_value") == null ? null : rs.getDouble("metric_value"),
                        rs.getString("unit"),
                        MetricScope.valueOf(rs.getString("scope_type")),
                        rs.getString("scope_id"),
                        rs.getLong("observations")))
                .list();
    }

    void saveSamples(UUID runId, String samplesJson) {
        client.sql("INSERT INTO simulation_samples (run_id, data) VALUES (:runId, CAST(:data AS JSONB))")
                .param("runId", runId)
                .param("data", samplesJson)
                .update();
    }

    Optional<String> findSamples(UUID runId) {
        return client.sql("SELECT data::text FROM simulation_samples WHERE run_id = :runId")
                .param("runId", runId)
                .query(String.class)
                .optional();
    }
}
