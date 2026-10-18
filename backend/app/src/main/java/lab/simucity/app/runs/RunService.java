package lab.simucity.app.runs;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lab.simucity.app.runs.RunDispatcher.RunCreated;
import lab.simucity.app.runs.RunDtos.MetricDto;
import lab.simucity.app.runs.RunDtos.ProjectRunItem;
import lab.simucity.app.runs.RunDtos.RunResponse;
import lab.simucity.app.runs.RunDtos.RunSummary;
import lab.simucity.app.scenarios.SavedScenario;
import lab.simucity.app.scenarios.ScenarioDefinition;
import lab.simucity.app.scenarios.ScenarioDtos.RunParametersRequest;
import lab.simucity.app.scenarios.ScenarioService;
import lab.simucity.app.shared.BadRequestException;
import lab.simucity.app.shared.ConflictException;
import lab.simucity.app.shared.NotFoundException;
import lab.simucity.app.shared.TooManyRequestsException;
import lab.simucity.app.shared.ValidationFailedException;
import lab.simucity.app.simulation.RunProperties;
import lab.simucity.app.simulation.SimulationConfig;
import lab.simucity.engine.EngineVersion;
import lab.simucity.engine.metrics.MetricScope;
import lab.simucity.engine.simulation.RunParameters;
import lab.simucity.engine.simulation.SimulationEngine;
import lab.simucity.engine.validation.ValidationResult;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RunService {

    private final SimulationRunRepository runs;
    private final RunBatchRepository batches;
    private final RunResultsStore results;
    private final RunCancellationRegistry cancellations;
    private final ScenarioService scenarios;
    private final SimulationEngine engine;
    private final RunProperties props;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;
    private final Clock clock;

    RunService(SimulationRunRepository runs, RunBatchRepository batches, RunResultsStore results,
            RunCancellationRegistry cancellations, ScenarioService scenarios, SimulationEngine engine,
            RunProperties props, ApplicationEventPublisher events, JsonMapper json, Clock clock) {
        this.runs = runs;
        this.batches = batches;
        this.results = results;
        this.cancellations = cancellations;
        this.scenarios = scenarios;
        this.engine = engine;
        this.props = props;
        this.events = events;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Valida y encola una ejecución. Devuelve enseguida en estado PENDING; el cálculo ocurre en segundo plano.
     * Sin semilla, se elige una aleatoria y queda registrada para poder repetir la ejecución.
     */
    @Transactional
    public SimulationRunRecord create(UUID ownerId, UUID scenarioId, RunParametersRequest request) {
        SavedScenario scenario = scenarios.getOwned(ownerId, scenarioId);

        requireCapacity(ownerId);
        long seed = request.seed() != null ? request.seed() : randomSeed();
        RunParameters params = validatedParameters(scenario, seed, request);

        SimulationRunRecord run = runs.save(newRun(null, scenario, ownerId, params));
        events.publishEvent(new RunCreated(List.of(run.getId())));
        return run;
    }

    /**
     * Lote de N ejecuciones con semillas consecutivas desde la semilla base. Se valida una vez (solo cambia la
     * semilla) y se calcula como una sola tarea: sus ejecuciones, una tras otra, en el mismo hilo del pool.
     */
    @Transactional
    public RunBatch createBatch(UUID ownerId, UUID scenarioId, int size, RunParametersRequest request) {
        if (size < RunBatch.MIN_SIZE || size > RunBatch.MAX_SIZE) {
            throw new BadRequestException("Un lote debe tener entre " + RunBatch.MIN_SIZE + " y "
                    + RunBatch.MAX_SIZE + " semillas.");
        }
        SavedScenario scenario = scenarios.getOwned(ownerId, scenarioId);
        requireCapacity(ownerId);
        // Acotada para que baseSeed + size no desborde.
        long baseSeed = request.seed() != null ? request.seed() : randomSeed() >>> 8;
        RunParameters first = validatedParameters(scenario, baseSeed, request);

        RunBatch batch = batches.save(new RunBatch(UUID.randomUUID(), scenario.getId(), ownerId, size, baseSeed,
                first.durationSeconds(), clock.instant()));
        List<UUID> runIds = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            RunParameters params = first.withSeed(Math.addExact(baseSeed, i));
            runIds.add(runs.save(newRun(batch.getId(), scenario, ownerId, params)).getId());
        }
        events.publishEvent(new RunCreated(runIds));
        return batch;
    }

    private void requireCapacity(UUID ownerId) {
        long active = runs.countActiveTasks(ownerId, RunStatus.ACTIVE);
        if (active >= props.maxActivePerUser()) {
            throw new TooManyRequestsException("Ya tienes " + active + " ejecuciones o lotes en curso; espera a "
                    + "que terminen o cancela alguno.");
        }
    }

    private RunParameters validatedParameters(SavedScenario scenario, long seed, RunParametersRequest request) {
        ScenarioDefinition definition = scenarios.definitionOf(scenario);
        Double sampleInterval = request.sampleIntervalSeconds();
        if (sampleInterval == null && request.durationSeconds() != null) {
            sampleInterval = SimulationConfig.autoSampleInterval(definition.edges().size(), request.durationSeconds(),
                    props.maxSampleCells());
        }
        RunParameters params = SimulationConfig.parameters(seed, request.durationSeconds(), request.dtSeconds(),
                sampleInterval, request.saturationFlowPerLaneVph(), request.stallThresholdSeconds());
        ValidationResult validation = engine.validate(definition.toEngine(), params);
        if (!validation.isValid()) {
            throw new ValidationFailedException("El escenario no se puede ejecutar: tiene errores.",
                    validation.issues());
        }
        return params;
    }

    private SimulationRunRecord newRun(UUID batchId, SavedScenario scenario, UUID ownerId, RunParameters params) {
        return new SimulationRunRecord(UUID.randomUUID(), batchId, scenario.getId(), ownerId, EngineVersion.CURRENT,
                params.seed(), params.dtSeconds(), params.durationSeconds(), json.writeValueAsString(params),
                scenario.getSchemaVersion(), scenario.getDefinitionJson(), clock.instant());
    }

    private static long randomSeed() {
        return ThreadLocalRandom.current().nextLong();
    }

    @Transactional(readOnly = true)
    public SimulationRunRecord getOwned(UUID ownerId, UUID runId) {
        return runs.findByIdAndOwnerId(runId, ownerId).orElseThrow(() -> new NotFoundException("Ejecución"));
    }

    @Transactional(readOnly = true)
    public Page<SimulationRunRecord> list(UUID ownerId, UUID scenarioId, Pageable pageable) {
        scenarios.getOwned(ownerId, scenarioId);
        return runs.findByScenarioIdAndOwnerId(scenarioId, ownerId, pageable);
    }

    /** Ejecuciones de todos los escenarios de un proyecto, con el nombre de su escenario. */
    @Transactional(readOnly = true)
    public Page<ProjectRunItem> listInProject(UUID ownerId, UUID projectId, Collection<RunStatus> statuses,
            Pageable pageable) {
        Map<UUID, String> names = scenarios.namesInProject(ownerId, projectId);
        if (names.isEmpty()) {
            return Page.empty(pageable);
        }
        return runs.findByOwnerIdAndScenarioIdInAndStatusIn(ownerId, names.keySet(), statuses, pageable)
                .map(r -> new ProjectRunItem(r.getId(), r.getScenarioId(), names.get(r.getScenarioId()), r.getStatus(),
                        r.getSeed(), r.getDurationSeconds(), r.getEngineVersion(), r.getCreatedAt(),
                        r.getFinishedAt()));
    }

    /**
     * Pendiente → se cancela al instante. En curso → se pide al motor que se detenga (lo hace en milisegundos)
     * y el estado pasa a CANCELLED cuando el worker termina. Ya finalizada → 409.
     */
    public SimulationRunRecord cancel(UUID ownerId, UUID runId) {
        SimulationRunRecord run = getOwned(ownerId, runId);
        if (run.getStatus() == RunStatus.PENDING
                && runs.cancelIfPending(runId, RunStatus.PENDING, RunStatus.CANCELLED, clock.instant()) == 1) {
            return getOwned(ownerId, runId);
        }
        run = getOwned(ownerId, runId);
        if (run.getStatus() == RunStatus.RUNNING) {
            cancellations.requestCancel(runId);
            return run;
        }
        throw new ConflictException("La ejecución ya terminó (" + run.getStatus() + ").");
    }

    @Transactional(readOnly = true)
    public List<MetricDto> metrics(UUID ownerId, UUID runId, MetricScope scope) {
        requireCompleted(getOwned(ownerId, runId));
        return results.findMetrics(runId, scope);
    }

    @Transactional(readOnly = true)
    public String samplesJson(UUID ownerId, UUID runId) {
        requireCompleted(getOwned(ownerId, runId));
        return results.findSamples(runId).orElseThrow(() -> new NotFoundException("Muestras"));
    }

    @Transactional(readOnly = true)
    public RunResponse toResponse(SimulationRunRecord run) {
        RunParameters params = json.readValue(run.getParametersSnapshot(), RunParameters.class);
        boolean completed = run.getStatus() == RunStatus.COMPLETED;
        RunSummary summary = completed ? json.readValue(run.getResultSummary(), RunSummary.class) : null;
        return new RunResponse(run.getId(), run.getScenarioId(), run.getStatus(), run.getEngineVersion(),
                run.getSeed(), params, run.getScenarioSchemaVersion(), run.getCreatedAt(), run.getStartedAt(),
                run.getFinishedAt(), run.getErrorCode(), run.getErrorDetail(),
                completed ? results.findMetrics(run.getId(), MetricScope.NETWORK) : null,
                completed ? summary.anomalies() : null,
                completed ? summary.warnings() : null);
    }

    private static void requireCompleted(SimulationRunRecord run) {
        if (run.getStatus() != RunStatus.COMPLETED) {
            throw new ConflictException("La ejecución todavía no tiene resultados (estado " + run.getStatus() + ").");
        }
    }
}
