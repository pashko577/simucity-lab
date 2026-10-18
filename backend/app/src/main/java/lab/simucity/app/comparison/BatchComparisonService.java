package lab.simucity.app.comparison;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lab.simucity.app.comparison.ComparisonDtos.BatchComparisonResponse;
import lab.simucity.app.comparison.ComparisonDtos.BatchRef;
import lab.simucity.app.comparison.ComparisonDtos.Condition;
import lab.simucity.app.comparison.FairnessCheck.RunFacts;
import lab.simucity.app.runs.BatchService;
import lab.simucity.app.runs.RunBatch;
import lab.simucity.app.runs.RunDtos.MetricDto;
import lab.simucity.app.runs.RunStatus;
import lab.simucity.app.runs.SimulationRunRecord;
import lab.simucity.app.scenarios.ScenarioCodec;
import lab.simucity.app.scenarios.ScenarioService;
import lab.simucity.app.shared.BadRequestException;
import lab.simucity.app.shared.ConflictException;
import lab.simucity.app.shared.NotFoundException;
import lab.simucity.engine.simulation.RunParameters;
import tools.jackson.databind.json.JsonMapper;

/** Compara dos lotes del usuario con estadística (sección 9.2: varias semillas, promedio y dispersión). */
@Service
public class BatchComparisonService {

    private static final int MIN_COMPLETED = 2;

    private final BatchService batches;
    private final ScenarioService scenarios;
    private final ScenarioCodec codec;
    private final JsonMapper json;

    BatchComparisonService(BatchService batches, ScenarioService scenarios, ScenarioCodec codec, JsonMapper json) {
        this.batches = batches;
        this.scenarios = scenarios;
        this.codec = codec;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public BatchComparisonResponse compare(UUID ownerId, UUID baseBatchId, UUID candidateBatchId) {
        if (baseBatchId.equals(candidateBatchId)) {
            throw new BadRequestException("Elige dos lotes distintos para comparar.");
        }
        RunBatch base = batches.getOwned(ownerId, baseBatchId);
        RunBatch candidate = batches.getOwned(ownerId, candidateBatchId);
        Map<Long, List<MetricDto>> baseMetrics = batches.networkMetricsBySeed(base);
        Map<Long, List<MetricDto>> candidateMetrics = batches.networkMetricsBySeed(candidate);
        if (baseMetrics.size() < MIN_COMPLETED || candidateMetrics.size() < MIN_COMPLETED) {
            throw new ConflictException("Cada lote necesita al menos " + MIN_COMPLETED
                    + " ejecuciones completadas para compararlo.");
        }

        RunFacts baseFacts = facts(base);
        RunFacts candidateFacts = facts(candidate);
        List<Condition> conditions = new ArrayList<>(FairnessCheck.conditions(baseFacts, candidateFacts));
        // En lotes, la condición no es "misma semilla" sino "mismo conjunto de semillas".
        conditions.removeIf(c -> c.code().equals("SAME_SEED"));
        conditions.add(1, new Condition("SAME_SEEDS", "Semillas del lote", baseMetrics.keySet().equals(
                candidateMetrics.keySet()), seeds(baseMetrics), seeds(candidateMetrics)));

        return new BatchComparisonResponse(
                ref(ownerId, base, baseMetrics),
                ref(ownerId, candidate, candidateMetrics),
                FairnessCheck.isFair(conditions),
                BatchComparator.paired(baseMetrics, candidateMetrics),
                conditions,
                ScenarioDiff.between(baseFacts.definition(), candidateFacts.definition()),
                BatchComparator.compare(baseMetrics, candidateMetrics));
    }

    /** Todas las ejecuciones de un lote comparten snapshot y parámetros salvo la semilla: basta la primera. */
    private RunFacts facts(RunBatch batch) {
        SimulationRunRecord first = batches.runsOf(batch).stream()
                .filter(r -> r.getStatus() == RunStatus.COMPLETED)
                .findFirst()
                .orElseThrow();
        return new RunFacts(first.getEngineVersion(),
                json.readValue(first.getParametersSnapshot(), RunParameters.class),
                codec.read(first.getScenarioSnapshot()));
    }

    private BatchRef ref(UUID ownerId, RunBatch batch, Map<Long, List<MetricDto>> metrics) {
        String name;
        try {
            name = scenarios.getOwned(ownerId, batch.getScenarioId()).getName();
        } catch (NotFoundException deleted) {
            name = "(escenario eliminado)";
        }
        String engineVersion = batches.runsOf(batch).getFirst().getEngineVersion();
        return new BatchRef(batch.getId(), batch.getScenarioId(), name, batch.getSize(), metrics.size(),
                List.copyOf(metrics.keySet()), batch.getDurationSeconds(), engineVersion);
    }

    private static String seeds(Map<Long, List<MetricDto>> metrics) {
        List<Long> seeds = List.copyOf(metrics.keySet());
        return seeds.size() <= 3 ? seeds.toString() : seeds.getFirst() + "…" + seeds.getLast() + " (" + seeds.size() + ")";
    }
}
