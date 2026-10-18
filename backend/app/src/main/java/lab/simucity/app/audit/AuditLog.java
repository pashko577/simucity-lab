package lab.simucity.app.audit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Registro de eventos de seguridad (sección 14.7). Un fallo al auditar nunca rompe la operación del usuario:
 * se registra en el log y se continúa.
 *
 * <p>Privacidad: se guarda el ID del usuario (no el email), la IP y un detalle sin datos sensibles. En un login
 * fallido no se guarda el email tecleado: a veces la gente escribe la contraseña en el campo equivocado.
 */
@Component
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);
    private static final int MAX_DETAILS = 500;

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Duration retention;

    AuditLog(JdbcClient jdbc, Clock clock, @Value("${simucity.audit.retention}") Duration retention) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.retention = retention;
    }

    /** Registra el evento con la IP de la petición en curso (si la hay). */
    public void record(AuditEventType type, UUID userId, String details) {
        record(type, userId, currentClientIp(), details);
    }

    /**
     * IP del cliente. Con {@code server.forward-headers-strategy=native}, Tomcat ya la toma de X-Forwarded-For
     * solo si la petición viene de un proxy interno de confianza (nginx en la red de Docker).
     */
    public static String currentClientIp() {
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                instanceof org.springframework.web.context.request.ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }

    public void record(AuditEventType type, UUID userId, String clientIp, String details) {
        try {
            jdbc.sql("""
                            INSERT INTO audit_events (occurred_at, event_type, user_id, client_ip, details)
                            VALUES (:at, :type, :user, :ip, :details)
                            """)
                    .param("at", java.sql.Timestamp.from(clock.instant()))
                    .param("type", type.name())
                    .param("user", userId)
                    .param("ip", truncate(clientIp, 45))
                    .param("details", truncate(details, MAX_DETAILS))
                    .update();
        } catch (RuntimeException e) {
            log.error("No se pudo registrar el evento de auditoría {}", type, e);
        }
    }

    /** Política de retención: borra a diario los eventos más antiguos que el plazo configurado. */
    @Scheduled(cron = "${simucity.audit.purge-cron}")
    public int purgeExpired() {
        Instant limit = clock.instant().minus(retention);
        int deleted = jdbc.sql("DELETE FROM audit_events WHERE occurred_at < :limit")
                .param("limit", java.sql.Timestamp.from(limit))
                .update();
        if (deleted > 0) {
            log.info("Auditoría: {} eventos anteriores a {} eliminados por retención", deleted, limit);
        }
        return deleted;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
