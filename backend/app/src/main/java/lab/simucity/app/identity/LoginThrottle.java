package lab.simucity.app.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Límite de intentos contra fuerza bruta (B2). Ventana deslizante en memoria:
 * <ul>
 *   <li>por cuenta (email normalizado): pocos fallos seguidos bloquean esa cuenta durante la ventana;</li>
 *   <li>por IP: muchos fallos desde una IP la bloquean (ataques contra muchas cuentas);</li>
 *   <li>registros por IP: frena la creación masiva de cuentas.</li>
 * </ul>
 * El bloqueo se aplica exista o no la cuenta, para no revelar qué emails están registrados.
 * En memoria: con varias instancias, cada una lleva su cuenta (ver docs/limitaciones.md).
 */
@Component
public class LoginThrottle {

    @ConfigurationProperties("simucity.security.throttle")
    public record Settings(
            int maxFailuresPerAccount,
            int maxFailuresPerIp,
            Duration failureWindow,
            int maxRegistrationsPerIp,
            Duration registrationWindow) {
    }

    private final Settings settings;
    private final Clock clock;
    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();

    LoginThrottle(Settings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    /** @return segundos hasta poder reintentar, o 0 si se permite intentar. */
    public long loginRetryAfterSeconds(String normalizedEmail, String ip) {
        return Math.max(
                retryAfter(accountKey(normalizedEmail), settings.maxFailuresPerAccount(), settings.failureWindow()),
                retryAfter(ipKey(ip), settings.maxFailuresPerIp(), settings.failureWindow()));
    }

    public void loginFailed(String normalizedEmail, String ip) {
        add(accountKey(normalizedEmail));
        add(ipKey(ip));
    }

    /** Un login correcto limpia los fallos de la cuenta (no los de la IP, compartida quizá con un atacante). */
    public void loginSucceeded(String normalizedEmail) {
        events.remove(accountKey(normalizedEmail));
    }

    public long registrationRetryAfterSeconds(String ip) {
        return retryAfter(registrationKey(ip), settings.maxRegistrationsPerIp(), settings.registrationWindow());
    }

    public void registered(String ip) {
        add(registrationKey(ip));
    }

    /** Limpieza periódica para que la memoria no crezca con claves antiguas. */
    @Scheduled(fixedDelayString = "PT10M")
    void purge() {
        Instant oldest = clock.instant().minus(maxWindow());
        events.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue(), oldest);
                return entry.getValue().isEmpty();
            }
        });
    }

    private long retryAfter(String key, int max, Duration window) {
        Deque<Instant> timestamps = events.get(key);
        if (timestamps == null) {
            return 0;
        }
        synchronized (timestamps) {
            Instant now = clock.instant();
            prune(timestamps, now.minus(window));
            if (timestamps.size() < max) {
                return 0;
            }
            // Se libera cuando el evento más antiguo de la ventana caduca.
            return Math.max(1, Duration.between(now, timestamps.peekFirst().plus(window)).toSeconds());
        }
    }

    private void add(String key) {
        Deque<Instant> timestamps = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            timestamps.addLast(clock.instant());
            prune(timestamps, clock.instant().minus(maxWindow()));
        }
    }

    /** Un evento cuenta mientras está estrictamente dentro de la ventana: a los 15 min exactos ya caducó. */
    private static void prune(Deque<Instant> timestamps, Instant oldest) {
        while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(oldest)) {
            timestamps.removeFirst();
        }
    }

    private Duration maxWindow() {
        return settings.failureWindow().compareTo(settings.registrationWindow()) > 0
                ? settings.failureWindow() : settings.registrationWindow();
    }

    private static String accountKey(String email) {
        return "account:" + email;
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }

    private static String registrationKey(String ip) {
        return "register:" + ip;
    }
}
