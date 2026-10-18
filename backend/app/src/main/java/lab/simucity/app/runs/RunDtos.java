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

    // --- Lotes ---------------------------------------------------------------------------------------

    /** Estado de un lote, derivado del de sus ejecuciones. */
    public enum BatchStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        /** Terminó, pero alguna ejecución falló o se canceló: las estadísticas usan solo las completadas. */
        PARTIAL,
        FAILED,
        CANCELLED;

        static BatchStatus of(List<RunStatus> statuses) {
            if (statuses.contains(RunStatus.RUNNING)) {
                return RUNNING;
            }
            if (statuses.contains(RunStatus.PENDING)) {
                return statuses.stream().allMatch(s -> s == RunStatus.PENDING) ? PENDING : RUNNING;
            }
            if (statuses.stream().allMatch(s -> s == RunStatus.COMPLETED)) {
                return COMPLETED;
            }
            if (statuses.stream().allMatch(s -> s == RunStatus.CANCELLED)) {
                return CANCELLED;
            }
            return statuses.contains(RunStatus.COMPLETED) ? PARTIAL : FAILED;
        }
    }

    public record BatchRunItem(UUID id, long seed, RunStatus status) {
    }

    /** Resumen de una métrica de red sobre las ejecuciones completadas del lote. */
    public record MetricSummary(String name, String unit, int n, Double mean, Double standardDeviation, Double min,
            Double max) {
    }

    public record BatchResponse(
            UUID id,
            UUID scenarioId,
            BatchStatus status,
            int size,
            long baseSeed,
            double durationSeconds,
            String engineVersion,
            Instant createdAt,
            List<BatchRunItem> runs,
            int completedRuns,
            List<MetricSummary> summary) {
    }

    public record BatchListItem(UUID id, UUID scenarioId, String scenarioName, BatchStatus status, int size,
            long baseSeed, double durationSeconds, Instant createdAt) {
    }

    /** Ejecución dentro del listado de un proyecto (para elegir qué comparar). */
    public record ProjectRunItem(UUID id, UUID scenarioId, String scenarioName, RunStatus status, long seed,
            double durationSeconds, String engineVersion, Instant createdAt, Instant finishedAt) {
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
