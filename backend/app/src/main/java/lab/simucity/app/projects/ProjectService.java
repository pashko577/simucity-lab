package lab.simucity.app.projects;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lab.simucity.app.projects.ProjectDtos.CreateProjectRequest;
import lab.simucity.app.projects.ProjectDtos.UpdateProjectRequest;
import lab.simucity.app.shared.NotFoundException;

/**
 * Casos de uso de proyectos. Cada método recibe el ID del usuario autenticado y solo opera sobre sus
 * propios proyectos; un proyecto ajeno se trata exactamente igual que uno inexistente (404).
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final Clock clock;

    ProjectService(ProjectRepository projects, Clock clock) {
        this.projects = projects;
        this.clock = clock;
    }

    @Transactional
    public Project create(UUID ownerId, CreateProjectRequest request) {
        Project project = new Project(UUID.randomUUID(), ownerId, request.name().strip(),
                blankToNull(request.description()), clock.instant());
        return projects.save(project);
    }

    @Transactional(readOnly = true)
    public Page<Project> list(UUID ownerId, Pageable pageable) {
        return projects.findByOwnerId(ownerId, pageable);
    }

    /** Punto único de autorización por recurso: usarlo también desde otros módulos (escenarios, ejecuciones). */
    @Transactional(readOnly = true)
    public Project getOwned(UUID ownerId, UUID projectId) {
        return projects.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new NotFoundException("Proyecto"));
    }

    @Transactional
    public Project update(UUID ownerId, UUID projectId, UpdateProjectRequest request) {
        Project project = getOwned(ownerId, projectId);
        Instant now = clock.instant();
        if (request.name() != null) {
            project.rename(request.name().strip(), now);
        }
        if (request.description() != null) {
            project.describe(blankToNull(request.description()), now);
        }
        return project;
    }

    @Transactional
    public void delete(UUID ownerId, UUID projectId) {
        projects.delete(getOwned(ownerId, projectId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
