package lab.simucity.app.scenarios;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Las búsquedas van siempre acotadas a un proyecto. El proyecto debe estar autorizado antes
 * (ver {@link ScenarioService#getOwned}).
 */
interface SavedScenarioRepository extends JpaRepository<SavedScenario, UUID> {

    Page<SavedScenario> findByProjectId(UUID projectId, Pageable pageable);

    Optional<SavedScenario> findByIdAndProjectId(UUID id, UUID projectId);

    /** Solo para resolver a qué proyecto pertenece un ID; el resultado se autoriza a continuación. */
    Optional<ProjectIdOnly> findProjectIdById(UUID id);

    interface ProjectIdOnly {
        UUID getProjectId();
    }
}
