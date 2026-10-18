package lab.simucity.app.runs;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lab.simucity.engine.metrics.Metric;
import lab.simucity.engine.metrics.MetricScope;
import lab.simucity.engine.simulation.Anomaly;
import lab.simucity.engine.simulation.RunParameters;
import lab.simucity.engine.simulation.Sample;
import lab.simucity.engine.validation.ValidationIssue;

public final class RunDtos {

    private RunDtos() {
    }

    public record MetricDto(String name, Double value, String unit, MetricScope scope, String scopeId,
            long observations) {

        static MetricDto from(Metric m) {
            return new MetricDto(m.name(), m.value(), m.unit(), m.scope(), m.scopeId(), m.observations());
        }
    }

    /** Resumen guardado en {@code result_summary}. */
    record RunSummary(long durationTicks, List<Anomaly> anomalies, List<ValidationIssue> warnings) {
    }

    public record RunListItem(UUID id, UUID scenarioId, RunStatus status, long seed, String engineVersion,
            Instant createdAt, Instant finishedAt) {

        static RunListItem from(SimulationRunRecord r) {
            return new RunListItem(r.getId(), r.getScenarioId(), r.getStatus(), r.getSeed(), r.getEngineVersion(),
                    r.getCreatedAt(), r.getFinishedAt());
        }
    }

    /**
     * Estado completo de una ejecución. {@code networkMetrics}, {@code anomalies} y {@code warnings} solo se
     * rellenan cuando terminó con éxito.
     */
    public record RunResponse(
            UUID id,
            UUID scenarioId,
            RunStatus status,
            String engineVersion,
            long seed,
            RunParameters parameters,
            int scenarioSchemaVersion,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            String errorCode,
            String errorDetail,
            List<MetricDto> networkMetrics,
            List<Anomaly> anomalies,
            List<ValidationIssue> warnings) {
    }

    /** Muestras en formato compacto: los arreglos siguen el orden de {@code edgeIds}. */
    record SamplesPayload(List<String> edgeIds, List<CompactSample> samples) {

        static SamplesPayload from(List<Sample> samples) {
            List<String> edgeIds = samples.isEmpty() ? List.of()
                    : samples.getFirst().edges().stream().map(Sample.EdgeSample::edgeId).toList();
            return new SamplesPayload(edgeIds, samples.stream().map(CompactSample::from).toList());
        }
    }

    record CompactSample(long tick, double t, int inNetwork, int queued, int waitingAtEntries, long completed,
            int[] vehicles, int[] queuedByEdge) {

        static CompactSample from(Sample s) {
            return new CompactSample(s.tick(), s.timeSeconds(), s.vehiclesInNetwork(), s.queuedVehicles(),
                    s.waitingAtEntries(), s.completedTrips(),
                    s.edges().stream().mapToInt(Sample.EdgeSample::vehicles).toArray(),
                    s.edges().stream().mapToInt(Sample.EdgeSample::queued).toArray());
        }
    }
}
