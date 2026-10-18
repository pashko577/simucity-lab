package lab.simucity.app.runs;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

/**
 * Señales de cancelación para ejecuciones en curso en esta instancia. El worker la consulta a través del
 * {@code RunControl} del motor. Solo funciona con una instancia (ver docs/limitaciones.md, B6).
 */
@Component
class RunCancellationRegistry {

    private final Map<UUID, AtomicBoolean> flags = new ConcurrentHashMap<>();

    AtomicBoolean register(UUID runId) {
        return flags.computeIfAbsent(runId, id -> new AtomicBoolean());
    }

    /** Puede llegar antes de que el worker registre la ejecución: se crea la señal y el worker la verá. */
    void requestCancel(UUID runId) {
        flags.computeIfAbsent(runId, id -> new AtomicBoolean()).set(true);
    }

    void remove(UUID runId) {
        flags.remove(runId);
    }
}
