package lab.simucity.app.runs;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Siempre filtrado por dueño, como las ejecuciones. */
interface RunBatchRepository extends JpaRepository<RunBatch, UUID> {

    Optional<RunBatch> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<RunBatch> findByOwnerIdAndScenarioIdIn(UUID ownerId, Collection<UUID> scenarioIds, Pageable pageable);
}
