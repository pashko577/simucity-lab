package lab.simucity.app.runs;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lab.simucity.app.runs.RunDtos.BatchListItem;
import lab.simucity.app.runs.RunDtos.BatchResponse;
import lab.simucity.app.runs.RunDtos.BatchRunItem;
import lab.simucity.app.runs.RunDtos.BatchStatus;
import lab.simucity.app.runs.RunDtos.MetricDto;
import lab.simucity.app.runs.RunDtos.MetricSummary;
import lab.simucity.app.scenarios.ScenarioService;
import lab.simucity.app.shared.ConflictException;
import lab.simucity.app.shared.NotFoundException;
import lab.simucity.engine.metrics.MetricScope;
import lab.simucity.engine.metrics.Statistics;

/** Consulta, resumen y cancelación de lotes. La creación vive en {@link RunService#createBatch}. */
@Service
public class BatchService {

    private final RunBatchRepository batches;
    private final SimulationRunRepository runs;
    private final RunResultsStore results;
    private final RunCancellationRegistry cancellations;
    private final ScenarioService scenarios;
    private final Clock clock;

    BatchService(RunBatchRepository batches, SimulationRunRepository runs, RunResultsStore results,
            RunCancellationRegistry cancellations, ScenarioService scenarios, Clock clock) {
        this.batches = batches;
        this.runs = runs;
        this.results = results;
        this.cancellations = cancellations;
        this.scenarios = scenarios;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RunBatch getOwned(UUID ownerId, UUID batchId) {
        return batches.findByIdAndOwnerId(batchId, ownerId).orElseThrow(() -> new NotFoundException("Lote"));
    }

    /** Ejecuciones del lote ordenadas por semilla: la posición i corresponde a la semilla baseSeed + i. */
    @Transactional(readOnly = true)
    public List<SimulationRunRecord> runsOf(RunBatch batch) {
        return runs.findByBatchIdOrderBySeed(batch.getId());
    }

    @Transactional(readOnly = true)
    public BatchStatus status(RunBatch batch) {
        return BatchStatus.of(runsOf(batch).stream().map(SimulationRunRecord::getStatus).toList());
    }

    /** Métricas de red de cada ejecución completada, por semilla. */
    @Transactional(readOnly = true)
    public Map<Long, List<MetricDto>> networkMetricsBySeed(RunBatch batch) {
        Map<Long, List<MetricDto>> bySeed = new LinkedHashMap<>();
        for (SimulationRunRecord run : runsOf(batch)) {
            if (run.getStatus() == RunStatus.COMPLETED) {
                bySeed.put(run.getSeed(), results.findMetrics(run.getId(), MetricScope.NETWORK));
            }
        }
        return bySeed;
    }

    @Transactional(readOnly = true)
    public BatchResponse toResponse(RunBatch batch) {
        List<SimulationRunRecord> batchRuns = runsOf(batch);
        Map<Long, List<MetricDto>> metrics = networkMetricsBySeed(batch);
        String engineVersion = batchRuns.isEmpty() ? null : batchRuns.getFirst().getEngineVersion();
        return new BatchResponse(batch.getId(), batch.getScenarioId(),
                BatchStatus.of(batchRuns.stream().map(SimulationRunRecord::getStatus).toList()),
                batch.getSize(), batch.getBaseSeed(), batch.getDurationSeconds(), engineVersion, batch.getCreatedAt(),
                batchRuns.stream().map(r -> new BatchRunItem(r.getId(), r.getSeed(), r.getStatus())).toList(),
                metrics.size(), summarize(metrics.values()));
    }

    @Transactional(readOnly = true)
    public Page<BatchListItem> listInProject(UUID ownerId, UUID projectId, Pageable pageable) {
        Map<UUID, String> names = scenarios.namesInProject(ownerId, projectId);
        if (names.isEmpty()) {
            return Page.empty(pageable);
        }
        return batches.findByOwnerIdAndScenarioIdIn(ownerId, names.keySet(), pageable)
                .map(b -> new BatchListItem(b.getId(), b.getScenarioId(), names.get(b.getScenarioId()), status(b),
                        b.getSize(), b.getBaseSeed(), b.getDurationSeconds(), b.getCreatedAt()));
    }

    /** Cancela las pendientes al instante y pide detenerse a la que esté en curso. 409 si ya terminó. */
    public BatchResponse cancel(UUID ownerId, UUID batchId) {
        RunBatch batch = getOwned(ownerId, batchId);
        List<SimulationRunRecord> batchRuns = runsOf(batch);
        if (batchRuns.stream().noneMatch(r -> RunStatus.ACTIVE.contains(r.getStatus()))) {
            throw new ConflictException("El lote ya terminó.");
        }
        for (SimulationRunRecord run : batchRuns) {
            if (run.getStatus() == RunStatus.PENDING) {
                runs.cancelIfPending(run.getId(), RunStatus.PENDING, RunStatus.CANCELLED, clock.instant());
            } else if (run.getStatus() == RunStatus.RUNNING) {
                cancellations.requestCancel(run.getId());
            }
        }
        return toResponse(batch);
    }

    /** Media, desviación, mínimo y máximo de cada métrica de red, en el orden en que la produce el motor. */
    static List<MetricSummary> summarize(java.util.Collection<List<MetricDto>> perRun) {
        Map<String, String> units = new LinkedHashMap<>();
        Map<String, List<Double>> values = new LinkedHashMap<>();
        for (List<MetricDto> metrics : perRun) {
            for (MetricDto m : metrics) {
                units.putIfAbsent(m.name(), m.unit());
                List<Double> list = values.computeIfAbsent(m.name(), k -> new ArrayList<>());
                if (m.value() != null) {
                    list.add(m.value());
                }
            }
        }
        List<MetricSummary> summary = new ArrayList<>();
        values.forEach((name, list) -> {
            if (list.isEmpty()) {
                summary.add(new MetricSummary(name, units.get(name), 0, null, null, null, null));
            } else {
                Statistics.Summary s = Statistics.summarize(list);
                summary.add(new MetricSummary(name, units.get(name), s.n(), s.mean(), s.standardDeviation(), s.min(),
                        s.max()));
            }
        });
        return summary;
    }
}
