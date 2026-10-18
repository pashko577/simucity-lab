package lab.simucity.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import lab.simucity.app.ApiTestSupport;
import lab.simucity.app.identity.AppUserPrincipal;

/** Lotes de varias semillas y su comparación estadística (sección 9.2). */
class BatchApiTest extends ApiTestSupport {

    private static final String SATURATED = STRAIGHT_ROAD
            .replace("\"vehiclesPerMinute\": 1, \"mode\": \"PERIODIC\"", "\"vehiclesPerMinute\": 40, \"mode\": \"POISSON\"");
    private static final String WIDENED = SATURATED
            .replace("\"lanes\": 1", "\"lanes\": 2")
            .replace("\"storageCapacity\": 10", "\"storageCapacity\": 20");
    private static final String MODERATE = STRAIGHT_ROAD
            .replace("\"vehiclesPerMinute\": 1, \"mode\": \"PERIODIC\"", "\"vehiclesPerMinute\": 20, \"mode\": \"POISSON\"");

    @Autowired
    private SimulationRunRepository runs;

    @Autowired
    private RunBatchRepository batches;

    @Test
    void lote_ejecutaNSemillasConsecutivas_yResumeCadaMetrica() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana), MODERATE);

        String batchId = createBatch(ana, scenarioId, 5, 100, 600);
        awaitBatch(ana, batchId, "COMPLETED");

        mvc.perform(get("/api/v1/batches/" + batchId).session(ana))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.completedRuns").value(5))
                .andExpect(jsonPath("$.runs[*].seed").value(org.hamcrest.Matchers.contains(100, 101, 102, 103, 104)))
                .andExpect(jsonPath("$.summary[?(@.name == 'trips_generated')].n").value(hasItem(5)))
                // Con llegadas aleatorias, cada semilla genera una cantidad distinta de vehículos.
                .andExpect(jsonPath("$.summary[?(@.name == 'trips_generated')].standardDeviation")
                        .value(everyItem(greaterThan(0.0))));
    }

    @Test
    void ampliarLaCalle_conMismasSemillas_mejoraDeFormaSignificativa() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String projectId = createProject(ana);
        String a = createBatch(ana, createScenario(ana, projectId, SATURATED), 5, 42, 900);
        String b = createBatch(ana, createScenario(ana, projectId, WIDENED), 5, 42, 900);
        awaitBatch(ana, a, "COMPLETED");
        awaitBatch(ana, b, "COMPLETED");

        MvcResult result = compare(ana, a, b)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paired").value(true))
                .andExpect(jsonPath("$.fair").value(true))
                .andExpect(jsonPath("$.conditions[?(@.code == 'SAME_SEEDS')].equal").value(hasItem(true)))
                .andExpect(jsonPath("$.network[?(@.name == 'trips_generated')].assessment").value(hasItem("UNCHANGED")))
                .andExpect(jsonPath("$.network[?(@.name == 'travel_time_mean')].assessment").value(hasItem("IMPROVED")))
                .andReturn();
        List<Number> ciHigh = JsonPath.read(result.getResponse().getContentAsString(),
                "$.network[?(@.name == 'travel_time_mean')].ciHigh");
        assertThat(ciHigh.getFirst().doubleValue()).isNegative();
    }

    /** Control: comparar un escenario consigo mismo con las mismas semillas no debe "encontrar" nada. */
    @Test
    void mismoEscenarioYMismasSemillas_todoSinCambios() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana), MODERATE);
        String a = createBatch(ana, scenarioId, 3, 7, 600);
        String b = createBatch(ana, scenarioId, 3, 7, 600);
        awaitBatch(ana, a, "COMPLETED");
        awaitBatch(ana, b, "COMPLETED");

        compare(ana, a, b)
                .andExpect(jsonPath("$.network[*].assessment").value(everyItem(is("UNCHANGED"))));
    }

    /**
     * Control: mismo escenario con semillas distintas. Las diferencias son solo azar, así que el tiempo de viaje
     * debe salir no concluyente (no una mejora o un empeoramiento inventados). Es determinista: siempre las
     * mismas semillas.
     */
    @Test
    void mismoEscenarioConSemillasDistintas_noConcluyente_yNoPareada() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana), MODERATE);
        String a = createBatch(ana, scenarioId, 5, 1, 600);
        String b = createBatch(ana, scenarioId, 5, 1001, 600);
        awaitBatch(ana, a, "COMPLETED");
        awaitBatch(ana, b, "COMPLETED");

        compare(ana, a, b)
                .andExpect(jsonPath("$.paired").value(false))
                .andExpect(jsonPath("$.fair").value(false))
                .andExpect(jsonPath("$.network[?(@.name == 'travel_time_mean')].assessment")
                        .value(hasItem("INCONCLUSIVE")));
    }

    @Test
    void tamanoInvalido_400() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana), MODERATE);

        for (int size : new int[] {1, 11}) {
            mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/batches").with(csrf()).session(ana)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"size\":" + size + ",\"durationSeconds\":60}"))
                    .andExpect(status().isBadRequest());
        }
    }

    /** Un lote de N ejecuciones cuenta como una sola tarea en el límite de 2 por usuario. */
    @Test
    void unLoteCuentaComoUnaTarea() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana), MODERATE);
        UUID owner = ownerOf(ana);
        insertPendingBatch(scenarioId, owner, 4);

        // 1 lote activo (4 ejecuciones) → aún cabe 1 tarea más.
        mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/runs").with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationSeconds\":60}"))
                .andExpect(status().isAccepted());
        insertPendingBatch(scenarioId, owner, 2);
        mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/batches").with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"size\":2,\"durationSeconds\":60}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void cancelarLotePendiente() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana), MODERATE);
        UUID batchId = insertPendingBatch(scenarioId, ownerOf(ana), 3);

        mvc.perform(post("/api/v1/batches/" + batchId + "/cancel").with(csrf()).session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.runs[*].status").value(everyItem(is("CANCELLED"))));
        mvc.perform(post("/api/v1/batches/" + batchId + "/cancel").with(csrf()).session(ana))
                .andExpect(status().isConflict());
    }

    @Test
    void otroUsuario_404() throws Exception {
        MockHttpSession ana = registerAndLogin();
        MockHttpSession beto = registerAndLogin();
        String anasProject = createProject(ana);
        String anasScenario = createScenario(ana, anasProject, MODERATE);
        String anasBatch = createBatch(ana, anasScenario, 2, 1, 60);
        awaitBatch(ana, anasBatch, "COMPLETED");
        String betosBatch = createBatch(beto, createScenario(beto, createProject(beto), MODERATE), 2, 1, 60);
        awaitBatch(beto, betosBatch, "COMPLETED");

        mvc.perform(get("/api/v1/batches/" + anasBatch).session(beto)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/batches/" + anasBatch + "/cancel").with(csrf()).session(beto))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/" + anasProject + "/batches").session(beto))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/scenarios/" + anasScenario + "/batches").with(csrf()).session(beto)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"size\":2,\"durationSeconds\":60}"))
                .andExpect(status().isNotFound());
        compare(beto, betosBatch, anasBatch).andExpect(status().isNotFound());
    }

    // --- Ayudantes ---

    private String createBatch(MockHttpSession session, String scenarioId, int size, long seed, int duration)
            throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/batches").with(csrf())
                        .session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"size\":" + size + ",\"seed\":" + seed + ",\"durationSeconds\":" + duration + "}"))
                .andExpect(status().isAccepted())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void awaitBatch(MockHttpSession session, String batchId, String expected) throws Exception {
        String state = "PENDING";
        for (int i = 0; i < 400 && (state.equals("PENDING") || state.equals("RUNNING")); i++) {
            Thread.sleep(50);
            state = JsonPath.read(mvc.perform(get("/api/v1/batches/" + batchId).session(session))
                    .andReturn().getResponse().getContentAsString(), "$.status");
        }
        assertThat(state).isEqualTo(expected);
    }

    private org.springframework.test.web.servlet.ResultActions compare(MockHttpSession session, String a, String b)
            throws Exception {
        return mvc.perform(post("/api/v1/comparisons/batches").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"baseBatchId\":\"" + a + "\",\"candidateBatchId\":\"" + b + "\"}"));
    }

    private UUID ownerOf(MockHttpSession session) {
        var context = (org.springframework.security.core.context.SecurityContext) session
                .getAttribute("SPRING_SECURITY_CONTEXT");
        return ((AppUserPrincipal) context.getAuthentication().getPrincipal()).id();
    }

    /** Lote PENDING que nunca se envía al pool: permite probar límites y cancelación sin carreras. */
    private UUID insertPendingBatch(String scenarioId, UUID owner, int size) {
        RunBatch batch = batches.save(new RunBatch(UUID.randomUUID(), UUID.fromString(scenarioId), owner, size, 1, 60,
                Instant.now()));
        for (int i = 0; i < size; i++) {
            runs.save(new SimulationRunRecord(UUID.randomUUID(), batch.getId(), UUID.fromString(scenarioId), owner,
                    "test", 1 + i, 0.5, 60,
                    "{\"seed\":" + (1 + i) + ",\"dtSeconds\":0.5,\"durationSeconds\":60,\"sampleIntervalSeconds\":5,"
                            + "\"saturationFlowPerLaneVph\":1800,\"stallThresholdSeconds\":300}",
                    1, "{}", Instant.now()));
        }
        return batch.getId();
    }
}
