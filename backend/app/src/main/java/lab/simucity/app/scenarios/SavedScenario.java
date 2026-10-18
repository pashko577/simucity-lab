package lab.simucity.app.scenarios;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Escenario guardado de un proyecto. Se llama así para no confundirlo con
 * {@link lab.simucity.engine.simulation.Scenario} del motor.
 */
@Entity
@Table(name = "scenarios")
public class SavedScenario {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    /** {@link ScenarioDefinition} serializado; la conversión la hace {@link ScenarioCodec}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String definition;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SavedScenario() {
    }

    SavedScenario(UUID id, UUID projectId, String name, String definitionJson, Instant now) {
        this.id = id;
        this.projectId = projectId;
        this.name = name;
        this.schemaVersion = ScenarioDefinition.SCHEMA_VERSION;
        this.definition = definitionJson;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void replace(String newName, String definitionJson, Instant now) {
        this.name = newName;
        this.definition = definitionJson;
        this.schemaVersion = ScenarioDefinition.SCHEMA_VERSION;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getDefinitionJson() {
        return definition;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
