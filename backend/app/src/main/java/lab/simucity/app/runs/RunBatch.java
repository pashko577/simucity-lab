package lab.simucity.app.runs;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Lote de ejecuciones: el mismo escenario con las semillas {@code baseSeed .. baseSeed + size - 1}.
 * Inmutable: su estado se deriva del de sus ejecuciones.
 */
@Entity
@Table(name = "run_batches")
public class RunBatch {

    public static final int MIN_SIZE = 2;
    public static final int MAX_SIZE = 10;

    @Id
    private UUID id;

    @Column(name = "scenario_id", nullable = false, updatable = false)
    private UUID scenarioId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, updatable = false)
    private int size;

    @Column(name = "base_seed", nullable = false, updatable = false)
    private long baseSeed;

    @Column(nullable = false, updatable = false)
    private double duration;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RunBatch() {
    }

    RunBatch(UUID id, UUID scenarioId, UUID ownerId, int size, long baseSeed, double duration, Instant now) {
        this.id = id;
        this.scenarioId = scenarioId;
        this.ownerId = ownerId;
        this.size = size;
        this.baseSeed = baseSeed;
        this.duration = duration;
        this.createdAt = now;
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

    public int getSize() {
        return size;
    }

    public long getBaseSeed() {
        return baseSeed;
    }

    public double getDurationSeconds() {
        return duration;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
