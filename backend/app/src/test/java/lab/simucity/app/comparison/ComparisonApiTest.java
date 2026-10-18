package lab.simucity.app.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import lab.simucity.app.ApiTestSupport;

/**
 * Experimento de la sección 2.2: una calle saturada (40 veh/min frente a 30 veh/min de capacidad con un carril)
 * comparada con la misma calle ampliada a dos carriles, misma semilla.
 *
 * <p>Ampliar es duplicar carriles <b>y</b> espacio: con solo más carriles, e2 (200 m, 20 s, 10 vehículos) seguiría
 * limitada a 10 veh / 20 s = 30 veh/min por almacenamiento. El motor lo modela así a propósito.
 */
class ComparisonApiTest extends ApiTestSupport {

    private static final String SATURATED = STRAIGHT_ROAD
            .replace("\"vehiclesPerMinute\": 1, \"mode\": \"PERIODIC\"", "\"vehiclesPerMinute\": 40, \"mode\": \"POISSON\"");
    private static final String TWO_LANES = SATURATED
            .replace("\"lanes\": 1", "\"lanes\": 2")
            .replace("\"storageCapacity\": 10", "\"storageCapacity\": 20");

    @Test
    void duplicarCarriles_mejoraElTiempoDeViaje_enUnaComparacionJusta() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String projectId = createProject(ana);
        String base = runAndAwait(ana, createScenario(ana, projectId, SATURATED), "{\"seed\":7,\"durationSeconds\":900}");
        String candidate = runAndAwait(ana, createScenario(ana, projectId, TWO_LANES), "{\"seed\":7,\"durationSeconds\":900}");

        MvcResult result = mvc.perform(post("/api/v1/comparisons").with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRunId\":\"" + base + "\",\"candidateRunId\":\"" + candidate + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fair").value(true))
                .andExpect(jsonPath("$.base.seed").value(7))
                .andExpect(jsonPath("$.base.scenarioName").value("Escenario"))
                // Qué cambió: en las dos calles, carriles 1 → 2 y capacidad 10 → 20, y nada más.
                .andExpect(jsonPath("$.changes.length()").value(2))
                .andExpect(jsonPath("$.changes[0].elementType").value("EDGE"))
                .andExpect(jsonPath("$.changes[0].fields.length()").value(2))
                .andExpect(jsonPath("$.changes[0].fields[0].field").value("lanes"))
                .andExpect(jsonPath("$.changes[0].fields[0].before").value(1))
                .andExpect(jsonPath("$.changes[0].fields[0].after").value(2))
                .andExpect(jsonPath("$.changes[0].fields[1].field").value("storageCapacity"))
                // Misma semilla y misma demanda ⇒ exactamente los mismos vehículos generados.
                .andExpect(jsonPath("$.network[?(@.name == 'trips_generated')].assessment").value(hasItem("UNCHANGED")))
                .andExpect(jsonPath("$.network[?(@.name == 'travel_time_mean')].assessment").value(hasItem("IMPROVED")))
                .andExpect(jsonPath("$.network[?(@.name == 'trips_completed')].assessment").value(hasItem("IMPROVED")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<Number> percent = JsonPath.read(body, "$.network[?(@.name == 'travel_time_mean')].percentChange");
        assertThat(percent.getFirst().doubleValue()).isLessThan(-20.0); // Mejora sustancial, no ruido.
        List<String> segmentIds = JsonPath.read(body, "$.segments[*].scopeId");
        assertThat(segmentIds).contains("e1", "e2");
    }

    @Test
    void semillasDistintas_seAdviertenEnLasCondiciones() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana), SATURATED);
        String a = runAndAwait(ana, scenarioId, "{\"seed\":1,\"durationSeconds\":300}");
        String b = runAndAwait(ana, scenarioId, "{\"seed\":2,\"durationSeconds\":300}");

        mvc.perform(post("/api/v1/comparisons").with(csrf()).session(ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRunId\":\"" + a + "\",\"candidateRunId\":\"" + b + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fair").value(false))
                .andExpect(jsonPath("$.conditions[?(@.code == 'SAME_SEED')].equal").value(hasItem(false)))
                .andExpect(jsonPath("$.changes.length()").value(0));
    }

    @Test
    void ejecucionesDelProyecto_conNombreDeEscenario_ySoloTerminadasPorDefecto() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String projectId = createProject(ana);
        String scenarioId = createScenario(ana, projectId, STRAIGHT_ROAD);
        runAndAwait(ana, scenarioId, "{\"seed\":1,\"durationSeconds\":60}");

        mvc.perform(get("/api/v1/projects/" + projectId + "/runs").session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].scenarioName").value("Escenario"))
                .andExpect(jsonPath("$.content[0].durationSeconds").value(60.0));
    }

    @Test
    void mismaEjecucion_400() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String run = runAndAwait(ana, createScenario(ana, createProject(ana), STRAIGHT_ROAD),
                "{\"seed\":1,\"durationSeconds\":60}");

        mvc.perform(post("/api/v1/comparisons").with(csrf()).session(ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRunId\":\"" + run + "\",\"candidateRunId\":\"" + run + "\"}"))
                .andExpect(status().isBadRequest());
    }

    /** IDOR: no se pueden comparar ni listar ejecuciones ajenas, ni mezclando una propia con una ajena. */
    @Test
    void otroUsuario_404() throws Exception {
        MockHttpSession ana = registerAndLogin();
        MockHttpSession beto = registerAndLogin();
        String anasProject = createProject(ana);
        String anasRun = runAndAwait(ana, createScenario(ana, anasProject, STRAIGHT_ROAD),
                "{\"seed\":1,\"durationSeconds\":60}");
        String betosRun = runAndAwait(beto, createScenario(beto, createProject(beto), STRAIGHT_ROAD),
                "{\"seed\":1,\"durationSeconds\":60}");

        mvc.perform(post("/api/v1/comparisons").with(csrf()).session(beto).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRunId\":\"" + betosRun + "\",\"candidateRunId\":\"" + anasRun + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/" + anasProject + "/runs").session(beto))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinCuerpoValido_400() throws Exception {
        MockHttpSession ana = registerAndLogin();

        mvc.perform(post("/api/v1/comparisons").with(csrf()).session(ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRunId\":\"no-es-uuid\"}"))
                .andExpect(status().isBadRequest());
    }

    private String runAndAwait(MockHttpSession session, String scenarioId, String body) throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/runs").with(csrf())
                        .session(session).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn();
        String runId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        String state = "PENDING";
        for (int i = 0; i < 200 && (state.equals("PENDING") || state.equals("RUNNING")); i++) {
            Thread.sleep(50);
            state = JsonPath.read(mvc.perform(get("/api/v1/runs/" + runId).session(session))
                    .andReturn().getResponse().getContentAsString(), "$.status");
        }
        assertThat(state).isEqualTo("COMPLETED");
        return runId;
    }
}
