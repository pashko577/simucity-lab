package lab.simucity.app.scenarios;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lab.simucity.app.audit.AuditEventType;
import lab.simucity.app.audit.AuditLog;
import lab.simucity.app.projects.ProjectService;
import lab.simucity.app.scenarios.ScenarioDtos.RunParametersRequest;
import lab.simucity.app.scenarios.ScenarioDtos.ScenarioRequest;
import lab.simucity.app.scenarios.ScenarioDtos.ScenarioResponse;
import lab.simucity.app.shared.NotFoundException;
import lab.simucity.app.simulation.SimulationConfig;
import lab.simucity.engine.simulation.SimulationEngine;
import lab.simucity.engine.validation.ValidationResult;

/**
 * Escenarios de un proyecto. La autorización pasa siempre por el proyecto:
 * un escenario es accesible solo si su proyecto pertenece al usuario.
 */
@Service
public class ScenarioService {

    /** Duración usada al validar sin parámetros explícitos. */
    private static final double DEFAULT_VALIDATION_DURATION_SECONDS = 3600;

    private final SavedScenarioRepository scenarios;
    private final ProjectService projects;
    private final ScenarioCodec codec;
    private final SimulationEngine engine;
    private final AuditLog audit;
    private final Clock clock;

    ScenarioService(SavedScenarioRepository scenarios, ProjectService projects, ScenarioCodec codec,
            SimulationEngine engine, AuditLog audit, Clock clock) {
        this.scenarios = scenarios;
        this.projects = projects;
        this.codec = codec;
        this.engine = engine;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public SavedScenario create(UUID ownerId, UUID projectId, ScenarioRequest request) {
        projects.getOwned(ownerId, projectId);
        ScenarioDefinition definition = request.definition() != null ? request.definition() : ScenarioDefinition.empty();
        return scenarios.save(new SavedScenario(UUID.randomUUID(), projectId, request.name().strip(),
                codec.write(definition), clock.instant()));
    }

    @Transactional(readOnly = true)
    public Page<SavedScenario> list(UUID ownerId, UUID projectId, Pageable pageable) {
        projects.getOwned(ownerId, projectId);
        return scenarios.findByProjectId(projectId, pageable);
    }

    /** Punto único de autorización de escenarios; también lo usan las ejecuciones. */
    @Transactional(readOnly = true)
    public SavedScenario getOwned(UUID ownerId, UUID scenarioId) {
        UUID projectId = scenarios.findProjectIdById(scenarioId)
                .orElseThrow(() -> new NotFoundException("Escenario"))
                .getProjectId();
        try {
            projects.getOwned(ownerId, projectId);
        } catch (NotFoundException ajeno) {
            throw new NotFoundException("Escenario"); // Mismo mensaje: no revelar que existe.
        }
        return scenarios.findByIdAndProjectId(scenarioId, projectId)
                .orElseThrow(() -> new NotFoundException("Escenario"));
    }

    @Transactional
    public SavedScenario replace(UUID ownerId, UUID scenarioId, ScenarioRequest request) {
        SavedScenario scenario = getOwned(ownerId, scenarioId);
        ScenarioDefinition definition = request.definition() != null ? request.definition() : ScenarioDefinition.empty();
        scenario.replace(request.name().strip(), codec.write(definition), clock.instant());
        return scenario;
    }

    @Transactional
    public SavedScenario duplicate(UUID ownerId, UUID scenarioId, String newName) {
        SavedScenario original = getOwned(ownerId, scenarioId);
        String name = newName != null && !newName.isBlank() ? newName.strip() : copyName(original.getName());
        Instant now = clock.instant();
        return scenarios.save(new SavedScenario(UUID.randomUUID(), original.getProjectId(), name,
                original.getDefinitionJson(), now));
    }

    @Transactional
    public void delete(UUID ownerId, UUID scenarioId) {
        scenarios.delete(getOwned(ownerId, scenarioId));
        audit.record(AuditEventType.SCENARIO_DELETED, ownerId, "escenario " + scenarioId);
    }

    @Transactional(readOnly = true)
    public ValidationResult validate(UUID ownerId, UUID scenarioId, RunParametersRequest params) {
        SavedScenario scenario = getOwned(ownerId, scenarioId);
        RunParametersRequest p = params != null ? params
                : new RunParametersRequest(null, DEFAULT_VALIDATION_DURATION_SECONDS, null, null, null, null);
        return engine.validate(definitionOf(scenario).toEngine(),
                SimulationConfig.parameters(0, p.durationSeconds(), p.dtSeconds(), p.sampleIntervalSeconds(),
                        p.saturationFlowPerLaneVph(), p.stallThresholdSeconds()));
    }

    /** Nombres de los escenarios de un proyecto autorizado, por ID. */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, String> namesInProject(UUID ownerId, UUID projectId) {
        projects.getOwned(ownerId, projectId);
        java.util.Map<UUID, String> names = new java.util.LinkedHashMap<>();
        scenarios.findByProjectIdOrderByName(projectId).forEach(s -> names.put(s.getId(), s.getName()));
        return names;
    }

    public ScenarioDefinition definitionOf(SavedScenario scenario) {
        return codec.read(scenario.getDefinitionJson());
    }

    public ScenarioResponse toResponse(SavedScenario s) {
        return new ScenarioResponse(s.getId(), s.getProjectId(), s.getName(), s.getSchemaVersion(), definitionOf(s),
                s.getCreatedAt(), s.getUpdatedAt());
    }

    private static String copyName(String name) {
        String copy = name + " (copia)";
        return copy.length() <= 120 ? copy : copy.substring(0, 120);
    }
}
