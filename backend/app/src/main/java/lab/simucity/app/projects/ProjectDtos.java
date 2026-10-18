package lab.simucity.app.projects;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description) {
    }

    /** Actualización parcial: los campos {@code null} no se modifican. */
    public record UpdateProjectRequest(
            @Size(min = 1, max = 120) @Pattern(regexp = ".*\\S.*", message = "El nombre no puede estar vacío.")
            String name,
            @Size(max = 2000) String description) {
    }

    public record ProjectResponse(
            UUID id,
            String name,
            String description,
            ProjectVisibility visibility,
            Instant createdAt,
            Instant updatedAt) {

        static ProjectResponse from(Project p) {
            return new ProjectResponse(p.getId(), p.getName(), p.getDescription(), p.getVisibility(),
                    p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
