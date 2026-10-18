package lab.simucity.app.scenarios;

import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/** Serializa definiciones de escenario a JSON y viceversa (columna JSONB y snapshots de ejecución). */
@Component
public class ScenarioCodec {

    private final JsonMapper json;

    ScenarioCodec(JsonMapper json) {
        this.json = json;
    }

    public String write(ScenarioDefinition definition) {
        return json.writeValueAsString(definition);
    }

    public ScenarioDefinition read(String definitionJson) {
        return json.readValue(definitionJson, ScenarioDefinition.class);
    }
}
