package lab.simucity.app.runs;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas siempre filtradas por dueño. Los cambios de estado concurrentes (worker frente a cancelación)
 * se hacen con UPDATE condicionales: solo uno de los dos gana, sin bloqueos.
 */
interface SimulationRunRepository extends JpaRepository<SimulationRunRecord, UUID> {

    Optional<SimulationRunRecord> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<SimulationRunRecord> findByScenarioIdAndOwnerId(UUID scenarioId, UUID ownerId, Pageable pageable);

    long countByOwnerIdAndStatusIn(UUID ownerId, Collection<RunStatus> statuses);

    /** PENDING → RUNNING. Devuelve 0 si otro proceso cambió el estado antes (p. ej. se canceló). */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update SimulationRunRecord r set r.status = :running, r.startedAt = :now "
            + "where r.id = :id and r.status = :pending")
    int transition(UUID id, RunStatus pending, RunStatus running, Instant now);

    default int markRunning(UUID id, Instant now) {
        return transition(id, RunStatus.PENDING, RunStatus.RUNNING, now);
    }

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update SimulationRunRecord r set r.status = :cancelled, r.finishedAt = :now "
            + "where r.id = :id and r.status = :pending")
    int cancelIfPending(UUID id, RunStatus pending, RunStatus cancelled, Instant now);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update SimulationRunRecord r set r.status = :failed, r.errorCode = :code, r.errorDetail = :detail, "
            + "r.finishedAt = :now where r.status in :from")
    int failAllIn(Collection<RunStatus> from, RunStatus failed, String code, String detail, Instant now);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update SimulationRunRecord r set r.status = :failed, r.errorCode = :code, r.errorDetail = :detail, "
            + "r.finishedAt = :now where r.id = :id and r.status = :pending")
    int failIfPending(UUID id, RunStatus pending, RunStatus failed, String code, String detail, Instant now);
}
