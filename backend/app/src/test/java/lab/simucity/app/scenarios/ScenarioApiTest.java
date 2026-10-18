package lab.simucity.app.scenarios;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import lab.simucity.app.ApiTestSupport;

class ScenarioApiTest extends ApiTestSupport {

    @Test
    void crearObtenerReemplazarYEliminar() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String projectId = createProject(ana);
        String id = createScenario(ana, projectId, STRAIGHT_ROAD);

        mvc.perform(get("/api/v1/scenarios/" + id).session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.definition.edges.length()").value(2))
                .andExpect(jsonPath("$.definition.demands[0].mode").value("PERIODIC"));

        mvc.perform(get("/api/v1/projects/" + projectId + "/scenarios").session(ana))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].definition").doesNotExist());

        mvc.perform(put("/api/v1/scenarios/" + id).with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Vacío\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Vacío"))
                .andExpect(jsonPath("$.definition.nodes.length()").value(0));

        mvc.perform(delete("/api/v1/scenarios/" + id).with(csrf()).session(ana))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/scenarios/" + id).session(ana)).andExpect(status().isNotFound());
    }

    @Test
    void duplicar_creaCopiaIndependiente() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String id = createScenario(ana, createProject(ana), STRAIGHT_ROAD);

        mvc.perform(post("/api/v1/scenarios/" + id + "/duplicate").with(csrf()).session(ana))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(not(id)))
                .andExpect(jsonPath("$.name").value("Escenario (copia)"))
                .andExpect(jsonPath("$.definition.edges.length()").value(2));
    }

    @Test
    void validar_escenarioCorrectoYEscenarioConErrores() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String projectId = createProject(ana);
        String valid = createScenario(ana, projectId, STRAIGHT_ROAD);
        String invalid = createScenario(ana, projectId,
                STRAIGHT_ROAD.replace("\"destination\": \"X\"", "\"destination\": \"NO_EXISTE\""));

        mvc.perform(post("/api/v1/scenarios/" + valid + "/validate").with(csrf()).session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        mvc.perform(post("/api/v1/scenarios/" + invalid + "/validate").with(csrf()).session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues[*].code").value(hasItem("DEMAND_UNKNOWN_DESTINATION")));
    }

    @Test
    void estructuraInvalida_400() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String projectId = createProject(ana);

        mvc.perform(post("/api/v1/projects/" + projectId + "/scenarios").with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"definition\":{\"nodes\":[{\"id\":\"\",\"type\":\"ENTRY\"}],"
                                + "\"edges\":[],\"demands\":[],\"signals\":[],\"prohibitedMovements\":[]}}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/projects/" + projectId + "/scenarios").with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"definition\":{\"nodes\":[{\"id\":\"A\",\"type\":\"VOLADOR\","
                                + "\"x\":0,\"y\":0}],\"edges\":[],\"demands\":[],\"signals\":[],"
                                + "\"prohibitedMovements\":[]}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "El cuerpo de la petición no es JSON válido o tiene campos con un tipo incorrecto."));
    }

    @Test
    void cuerpoDemasiadoGrande_413() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String projectId = createProject(ana);
        String huge = "{\"name\":\"" + "x".repeat(3 * 1024 * 1024) + "\"}";

        mvc.perform(post("/api/v1/projects/" + projectId + "/scenarios").with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON).content(huge))
                .andExpect(status().is(413));
    }

    /** IDOR: los escenarios de otro usuario no existen para él, ni directamente ni por su proyecto. */
    @Test
    void otroUsuario_recibe404EnTodo() throws Exception {
        MockHttpSession ana = registerAndLogin();
        MockHttpSession beto = registerAndLogin();
        String projectId = createProject(ana);
        String id = createScenario(ana, projectId, STRAIGHT_ROAD);
        String body = "{\"name\":\"Hackeado\"}";

        mvc.perform(get("/api/v1/scenarios/" + id).session(beto)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/scenarios/" + id).with(csrf()).session(beto)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/scenarios/" + id).with(csrf()).session(beto)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/scenarios/" + id + "/duplicate").with(csrf()).session(beto))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/scenarios/" + id + "/validate").with(csrf()).session(beto))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/" + projectId + "/scenarios").session(beto))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/projects/" + projectId + "/scenarios").with(csrf()).session(beto)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/scenarios/" + id).session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Escenario"));
    }

    @Test
    void sinSesion_401() throws Exception {
        mvc.perform(get("/api/v1/scenarios/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isUnauthorized());
    }
}
