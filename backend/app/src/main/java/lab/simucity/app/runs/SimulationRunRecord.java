package lab.simucity.app.runs;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una ejecución del motor. Todo lo necesario para repetirla (snapshot del escenario, parámetros, semilla y
 * versión del motor) se fija al crearla y no cambia nunca (sección 7.3).
 */
@Entity
@Table(name = "simulation_runs")
public class SimulationRunRecord {

    @Id
    private UUID id;

    @Column(name = "scenario_id", nullable = false, updatable = false)
    private UUID scenarioId;

    /** Copia del dueño del proyecto: autoriza sin recorrer escenario → proyecto, y permite contar activas. */
    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @Column(name = "engine_version", nullable = false, updatable = false)
    private String engineVersion;

    @Column(nullable = false, updatable = false)
    private long seed;

    @Column(name = "time_step", nullable = false, updatable = false)
    private double timeStep;

    @Column(nullable = false, updatable = false)
    private double duration;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_snapshot", nullable = false, updatable = false)
    private String parametersSnapshot;

    @Column(name = "scenario_schema_version", nullable = false, updatable = false)
    private int scenarioSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scenario_snapshot", nullable = false, updatable = false)
    private String scenarioSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_summary")
    private String resultSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_code")
    private String errorCode;

    /** Mensaje para el usuario; nunca el texto de una excepción interna. */
    @Column(name = "error_detail")
    private String errorDetail;

    protected SimulationRunRecord() {
    }

    SimulationRunRecord(UUID id, UUID scenarioId, UUID ownerId, String engineVersion, long seed, double timeStep,
            double duration, String parametersSnapshot, int scenarioSchemaVersion, String scenarioSnapshot,
            Instant now) {
        this.id = id;
        this.scenarioId = scenarioId;
        this.ownerId = ownerId;
        this.status = RunStatus.PENDING;
        this.engineVersion = engineVersion;
        this.seed = seed;
        this.timeStep = timeStep;
        this.duration = duration;
        this.parametersSnapshot = parametersSnapshot;
        this.scenarioSchemaVersion = scenarioSchemaVersion;
        this.scenarioSnapshot = scenarioSnapshot;
        this.createdAt = now;
    }

    void complete(String summaryJson, Instant now) {
        this.status = RunStatus.COMPLETED;
        this.resultSummary = summaryJson;
        this.finishedAt = now;
    }

    void fail(String code, String userMessage, Instant now) {
        this.status = RunStatus.FAILED;
        this.errorCode = code;
        this.errorDetail = userMessage;
        this.finishedAt = now;
    }

    void cancel(Instant now) {
        this.status = RunStatus.CANCELLED;
        this.finishedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getScenarioId() {
        return scenarioId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public RunStatus getStatus() {
        return status;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public long getSeed() {
        return seed;
    }

    public double getDurationSeconds() {
        return duration;
    }

    public String getParametersSnapshot() {
        return parametersSnapshot;
    }

    public int getScenarioSchemaVersion() {
        return scenarioSchemaVersion;
    }

    public String getScenarioSnapshot() {
        return scenarioSnapshot;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorDetail() {
        return errorDetail;
    }
}
