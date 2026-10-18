package lab.simucity.app.scenarios;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lab.simucity.engine.validation.ValidationIssue;
import lab.simucity.engine.validation.ValidationResult;

public final class ScenarioDtos {

    private ScenarioDtos() {
    }

    /** Crear o reemplazar (PUT) un escenario. Sin {@code definition} al crear, empieza vacío. */
    public record ScenarioRequest(
            @NotBlank @Size(max = 120) String name,
            @Valid ScenarioDefinition definition) {
    }

    public record DuplicateRequest(@Size(min = 1, max = 120) String name) {
    }

    public record ScenarioSummary(UUID id, UUID projectId, String name, Instant createdAt, Instant updatedAt) {

        static ScenarioSummary from(SavedScenario s) {
            return new ScenarioSummary(s.getId(), s.getProjectId(), s.getName(), s.getCreatedAt(), s.getUpdatedAt());
        }
    }

    public record ScenarioResponse(
            UUID id,
            UUID projectId,
            String name,
            int schemaVersion,
            ScenarioDefinition definition,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ValidationResponse(boolean valid, List<ValidationIssue> issues) {

        static ValidationResponse from(ValidationResult result) {
            return new ValidationResponse(result.isValid(), result.issues());
        }
    }

    /** Parámetros de ejecución; los ausentes toman los valores por defecto del motor. */
    public record RunParametersRequest(
            Long seed,
            @NotNull Double durationSeconds,
            Double dtSeconds,
            Double sampleIntervalSeconds,
            Double saturationFlowPerLaneVph,
            Double stallThresholdSeconds) {
    }
}
