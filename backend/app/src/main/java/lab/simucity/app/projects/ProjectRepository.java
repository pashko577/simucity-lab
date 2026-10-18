package lab.simucity.app.projects;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Todas las consultas filtran por dueño. No exponer métodos que busquen solo por ID:
 * así es imposible olvidar la comprobación de autorización.
 */
interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<Project> findByOwnerId(UUID ownerId, Pageable pageable);
}
