package lab.simucity.app.runs;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía ejecuciones al pool de simulación cuando la transacción que las creó se confirma (si se enviara
 * antes, el worker podría no encontrar el registro). También limpia ejecuciones interrumpidas al arrancar.
 */
@Component
class RunDispatcher {

    record RunCreated(UUID runId) {
    }

    private static final Logger log = LoggerFactory.getLogger(RunDispatcher.class);

    private final ThreadPoolTaskExecutor simulationExecutor;
    private final RunWorker worker;
    private final SimulationRunRepository runs;
    private final Clock clock;

    RunDispatcher(ThreadPoolTaskExecutor simulationExecutor, RunWorker worker, SimulationRunRepository runs,
            Clock clock) {
        this.simulationExecutor = simulationExecutor;
        this.worker = worker;
        this.runs = runs;
        this.clock = clock;
    }

    @TransactionalEventListener
    void onRunCreated(RunCreated event) {
        try {
            simulationExecutor.execute(() -> worker.execute(event.runId()));
        } catch (TaskRejectedException queueFull) {
            runs.failIfPending(event.runId(), RunStatus.PENDING, RunStatus.FAILED, "QUEUE_FULL",
                    "El servidor está ocupado. Inténtalo de nuevo en unos minutos.", clock.instant());
        }
    }

    /**
     * Las ejecuciones viven en memoria del proceso: si el servidor se reinició, las que quedaron a medias
     * no van a terminar. Se marcan como fallidas para que el usuario pueda relanzarlas.
     */
    @EventListener(ApplicationReadyEvent.class)
    void failInterruptedRuns() {
        int interrupted = runs.failAllIn(RunStatus.ACTIVE, RunStatus.FAILED, "INTERRUPTED",
                "El servidor se reinició durante la ejecución. Vuelve a lanzarla.", clock.instant());
        if (interrupted > 0) {
            log.warn("{} ejecuciones interrumpidas por el reinicio se marcaron como FAILED", interrupted);
        }
    }
}
