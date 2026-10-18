package lab.simucity.app.runs;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import lab.simucity.app.runs.RunDtos.RunSummary;
import lab.simucity.app.runs.RunDtos.SamplesPayload;
import lab.simucity.app.scenarios.ScenarioCodec;
import lab.simucity.app.simulation.RunProperties;
import lab.simucity.engine.simulation.InvalidScenarioException;
import lab.simucity.engine.simulation.RunControl;
import lab.simucity.engine.simulation.RunParameters;
import lab.simucity.engine.simulation.Scenario;
import lab.simucity.engine.simulation.SimulationAbortedException;
import lab.simucity.engine.simulation.SimulationEngine;
import lab.simucity.engine.simulation.SimulationResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ejecuta una simulación en un hilo del pool {@code simulationExecutor}. Trabaja solo con el snapshot
 * guardado en la ejecución, nunca con el escenario actual: editarlo después no altera el resultado.
 */
@Component
class RunWorker {

    private static final Logger log = LoggerFactory.getLogger(RunWorker.class);

    private final SimulationRunRepository runs;
    private final RunResultsStore results;
    private final RunCancellationRegistry cancellations;
    private final SimulationEngine engine;
    private final ScenarioCodec scenarioCodec;
    private final JsonMapper json;
    private final RunProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;

    RunWorker(SimulationRunRepository runs, RunResultsStore results, RunCancellationRegistry cancellations,
            SimulationEngine engine, ScenarioCodec scenarioCodec, JsonMapper json, RunProperties props,
            TransactionTemplate tx, Clock clock) {
        this.runs = runs;
        this.results = results;
        this.cancellations = cancellations;
        this.engine = engine;
        this.scenarioCodec = scenarioCodec;
        this.json = json;
        this.props = props;
        this.tx = tx;
        this.clock = clock;
    }

    void execute(UUID runId) {
        if (runs.markRunning(runId, clock.instant()) == 0) {
            return; // Se canceló o falló mientras esperaba en la cola.
        }
        AtomicBoolean cancelRequested = cancellations.register(runId);
        long deadline = System.nanoTime() + props.timeout().toNanos();
        AtomicBoolean timedOut = new AtomicBoolean();
        RunControl control = () -> {
            if (cancelRequested.get()) {
                return true;
            }
            if (System.nanoTime() > deadline) {
                timedOut.set(true);
                return true;
            }
            return false;
        };

        try {
            SimulationRunRecord run = runs.findById(runId).orElseThrow();
            Scenario scenario = scenarioCodec.read(run.getScenarioSnapshot()).toEngine();
            RunParameters params = json.readValue(run.getParametersSnapshot(), RunParameters.class);

            SimulationResult result = engine.run(scenario, params, control);

            tx.executeWithoutResult(status -> storeResult(runId, result));
        } catch (SimulationAbortedException aborted) {
            if (timedOut.get()) {
                finish(runId, r -> r.fail("TIMEOUT", "La simulación superó el tiempo máximo de cálculo ("
                        + props.timeout().toSeconds() + " s). Reduzca la duración o el tamaño de la red.",
                        clock.instant()));
            } else {
                finish(runId, r -> r.cancel(clock.instant()));
            }
        } catch (InvalidScenarioException invalid) {
            finish(runId, r -> r.fail("INVALID_SCENARIO", "El escenario no es válido.", clock.instant()));
        } catch (RuntimeException unexpected) {
            log.error("Fallo inesperado en la ejecución {}", runId, unexpected);
            finish(runId, r -> r.fail("ENGINE_ERROR", "Error interno al ejecutar la simulación.", clock.instant()));
        } finally {
            cancellations.remove(runId);
        }
    }

    private void storeResult(UUID runId, SimulationResult result) {
        SimulationRunRecord run = runs.findById(runId).orElseThrow();
        if (run.getStatus() != RunStatus.RUNNING) {
            return;
        }
        results.saveMetrics(runId, result.metrics());
        results.saveSamples(runId, json.writeValueAsString(SamplesPayload.from(result.samples())));
        RunSummary summary = new RunSummary(result.durationTicks(), result.anomalies(), result.warnings());
        run.complete(json.writeValueAsString(summary), clock.instant());
    }

    private void finish(UUID runId, java.util.function.Consumer<SimulationRunRecord> change) {
        tx.executeWithoutResult(status -> runs.findById(runId)
                .filter(r -> r.getStatus() == RunStatus.RUNNING)
                .ifPresent(change));
    }
}
