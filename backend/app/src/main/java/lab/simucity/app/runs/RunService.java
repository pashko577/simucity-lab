package lab.simucity.app.runs;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lab.simucity.app.runs.RunDispatcher.RunCreated;
import lab.simucity.app.runs.RunDtos.MetricDto;
import lab.simucity.app.runs.RunDtos.RunResponse;
import lab.simucity.app.runs.RunDtos.RunSummary;
import lab.simucity.app.scenarios.SavedScenario;
import lab.simucity.app.scenarios.ScenarioDtos.RunParametersRequest;
import lab.simucity.app.scenarios.ScenarioService;
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
    private final RunResultsStore results;
    private final RunCancellationRegistry cancellations;
    private final ScenarioService scenarios;
    private final SimulationEngine engine;
    private final RunProperties props;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;
    private final Clock clock;

    RunService(SimulationRunRepository runs, RunResultsStore results, RunCancellationRegistry cancellations,
            ScenarioService scenarios, SimulationEngine engine, RunProperties props, ApplicationEventPublisher events,
            JsonMapper json, Clock clock) {
        this.runs = runs;
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

        long active = runs.countByOwnerIdAndStatusIn(ownerId, RunStatus.ACTIVE);
        if (active >= props.maxActivePerUser()) {
            throw new TooManyRequestsException("Ya tienes " + active + " ejecuciones en curso; espera a que "
                    + "terminen o cancela alguna.");
        }

        long seed = request.seed() != null ? request.seed() : ThreadLocalRandom.current().nextLong();
        RunParameters params = SimulationConfig.parameters(seed, request.durationSeconds(), request.dtSeconds(),
                request.sampleIntervalSeconds(), request.saturationFlowPerLaneVph(), request.stallThresholdSeconds());

        ValidationResult validation = engine.validate(scenarios.definitionOf(scenario).toEngine(), params);
        if (!validation.isValid()) {
            throw new ValidationFailedException("El escenario no se puede ejecutar: tiene errores.",
                    validation.issues());
        }

        SimulationRunRecord run = runs.save(new SimulationRunRecord(UUID.randomUUID(), scenario.getId(), ownerId,
                EngineVersion.CURRENT, seed, params.dtSeconds(), params.durationSeconds(),
                json.writeValueAsString(params), scenario.getSchemaVersion(), scenario.getDefinitionJson(),
                clock.instant()));
        events.publishEvent(new RunCreated(run.getId()));
        return run;
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
