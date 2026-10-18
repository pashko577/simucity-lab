package lab.simucity.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import lab.simucity.app.ApiTestSupport;
import lab.simucity.app.identity.AppUserPrincipal;

class RunApiTest extends ApiTestSupport {

    @Autowired
    private SimulationRunRepository runs;

    @Test
    void ejecutar_terminaEnSegundoPlanoConLasMetricasEsperadas() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana), STRAIGHT_ROAD);

        MvcResult created = mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/runs").with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"seed\":42,\"durationSeconds\":100}"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.seed").value(42))
                .andExpect(jsonPath("$.engineVersion").value(lab.simucity.engine.EngineVersion.CURRENT))
                .andReturn();
        String runId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        awaitStatus(ana, runId, "COMPLETED");

        mvc.perform(get("/api/v1/runs/" + runId).session(ana))
                .andExpect(jsonPath("$.parameters.durationSeconds").value(100.0))
                .andExpect(jsonPath("$.networkMetrics[?(@.name == 'trips_completed')].value").value(hasItem(1.0)))
                .andExpect(jsonPath("$.networkMetrics[?(@.name == 'travel_time_mean')].value").value(hasItem(30.0)))
                .andExpect(jsonPath("$.anomalies.length()").value(0));

        mvc.perform(get("/api/v1/runs/" + runId + "/metrics").param("scope", "SEGMENT").session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'vehicles_discharged' && @.scopeId == 'e2')].value")
                        .value(hasItem(1.0)));

        mvc.perform(get("/api/v1/runs/" + runId + "/samples").session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edgeIds[0]").value("e1"))
                .andExpect(jsonPath("$.samples.length()").value(20)); // 100 s / muestra cada 5 s

        mvc.perform(get("/api/v1/scenarios/" + scenarioId + "/runs").session(ana))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void mismaSemilla_mismosResultados() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana),
                STRAIGHT_ROAD.replace("\"vehiclesPerMinute\": 1, \"mode\": \"PERIODIC\"",
                        "\"vehiclesPerMinute\": 20, \"mode\": \"POISSON\""));
        String body = "{\"seed\":2024,\"durationSeconds\":600}";

        String first = runAndAwait(ana, scenarioId, body);
        String second = runAndAwait(ana, scenarioId, body);

        String metricsA = mvc.perform(get("/api/v1/runs/" + first + "/metrics").session(ana))
                .andReturn().getResponse().getContentAsString();
        String metricsB = mvc.perform(get("/api/v1/runs/" + second + "/metrics").session(ana))
                .andReturn().getResponse().getContentAsString();
        assertThat(metricsA).isEqualTo(metricsB).contains("trips_generated");
    }

    /** Sección 12.2: editar el escenario después no altera los resultados ni el snapshot de una ejecución. */
    @Test
    void editarElEscenario_noAfectaEjecucionesAnteriores() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana), STRAIGHT_ROAD);
        String runId = runAndAwait(ana, scenarioId, "{\"seed\":1,\"durationSeconds\":100}");

        mvc.perform(put("/api/v1/scenarios/" + scenarioId).with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Más larga\",\"definition\":"
                                + STRAIGHT_ROAD.replace("\"lengthMeters\": 200", "\"lengthMeters\": 400") + "}"))
                .andExpect(status().isOk());
        // 500 m a 10 m/s = 50 s; con 200 s de duración terminan los vehículos generados en 59,5 s y 119,5 s.
        String newRunId = runAndAwait(ana, scenarioId, "{\"seed\":1,\"durationSeconds\":200}");

        mvc.perform(get("/api/v1/runs/" + runId).session(ana))
                .andExpect(jsonPath("$.networkMetrics[?(@.name == 'travel_time_mean')].value").value(hasItem(30.0)));
        mvc.perform(get("/api/v1/runs/" + newRunId).session(ana))
                .andExpect(jsonPath("$.networkMetrics[?(@.name == 'travel_time_mean')].value").value(hasItem(50.0)));
        assertThat(runs.findById(UUID.fromString(runId)).orElseThrow().getScenarioSnapshot())
                .contains("200").doesNotContain("400");
    }

    @Test
    void escenarioConErrores_400ConLaListaDeProblemas() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana),
                STRAIGHT_ROAD.replace("\"lengthMeters\": 100", "\"lengthMeters\": -5"));

        mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/runs").with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationSeconds\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.issues[*].code").value(hasItem("EDGE_INVALID_LENGTH")));
    }

    @Test
    void duracionSobreElLimiteDelServidor_400() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana), STRAIGHT_ROAD);

        mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/runs").with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationSeconds\":99999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.issues[*].code").value(hasItem("PARAM_INVALID_DURATION")));
    }

    @Test
    void sinSemilla_seEligeUnaYQuedaRegistrada() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana), STRAIGHT_ROAD);

        mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/runs").with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationSeconds\":60}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.seed").isNumber())
                .andExpect(jsonPath("$.parameters.seed").isNumber());
    }

    @Test
    void limiteDeEjecucionesSimultaneas_429() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana), STRAIGHT_ROAD);
        UUID owner = ownerOf(ana);
        insertPendingRun(scenarioId, owner);
        insertPendingRun(scenarioId, owner);

        mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/runs").with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationSeconds\":60}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void cancelarPendiente_yNoSePuedeCancelarUnaTerminada() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana), STRAIGHT_ROAD);
        UUID pending = insertPendingRun(scenarioId, ownerOf(ana));

        mvc.perform(post("/api/v1/runs/" + pending + "/cancel").with(csrf()).session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/v1/runs/" + pending + "/cancel").with(csrf()).session(ana))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/runs/" + pending + "/metrics").session(ana))
                .andExpect(status().isConflict());
    }

    @Test
    void exportarCsv_conCabeceraYProteccionContraFormulas() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana),
                STRAIGHT_ROAD.replace("\"e2\"", "\"=HYPERLINK(1)\""));
        String runId = runAndAwait(ana, scenarioId, "{\"seed\":1,\"durationSeconds\":100}");

        mvc.perform(get("/api/v1/runs/" + runId + "/export.csv").session(ana))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().string(containsString(
                        "run_id,engine_version,seed,metric_name,metric_value,unit,scope_type,scope_id,observations")))
                .andExpect(content().string(containsString(",trips_completed,1.0,veh,NETWORK,,")))
                .andExpect(content().string(containsString(",SEGMENT,'=HYPERLINK(1),")));
    }

    /** IDOR: las ejecuciones de otro usuario no existen para él (sección 14.2, también descargas). */
    @Test
    void otroUsuario_recibe404EnEjecucionesYDescargas() throws Exception {
        MockHttpSession ana = registerAndLogin();
        MockHttpSession beto = registerAndLogin();
        String scenarioId = createScenario(ana, createProject(ana), STRAIGHT_ROAD);
        String runId = runAndAwait(ana, scenarioId, "{\"seed\":1,\"durationSeconds\":60}");

        mvc.perform(get("/api/v1/runs/" + runId).session(beto)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/runs/" + runId + "/metrics").session(beto)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/runs/" + runId + "/samples").session(beto)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/runs/" + runId + "/export.csv").session(beto)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/runs/" + runId + "/cancel").with(csrf()).session(beto))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/scenarios/" + scenarioId + "/runs").session(beto))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/runs").with(csrf()).session(beto)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationSeconds\":60}"))
                .andExpect(status().isNotFound());
    }

    private String runAndAwait(MockHttpSession session, String scenarioId, String body) throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/runs").with(csrf())
                        .session(session).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn();
        String runId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        awaitStatus(session, runId, "COMPLETED");
        return runId;
    }

    private void awaitStatus(MockHttpSession session, String runId, String expected) throws Exception {
        String status = null;
        for (int i = 0; i < 200; i++) {
            status = JsonPath.read(mvc.perform(get("/api/v1/runs/" + runId).session(session))
                    .andReturn().getResponse().getContentAsString(), "$.status");
            if (!status.equals("PENDING") && !status.equals("RUNNING")) {
                break;
            }
            Thread.sleep(50);
        }
        assertThat(status).as("estado final de la ejecución").isEqualTo(expected);
    }

    private UUID ownerOf(MockHttpSession session) {
        var context = (org.springframework.security.core.context.SecurityContext) session
                .getAttribute("SPRING_SECURITY_CONTEXT");
        return ((AppUserPrincipal) context.getAuthentication().getPrincipal()).id();
    }

    /** Ejecución PENDING que nunca se envía al pool: permite probar límites y cancelación sin carreras. */
    private UUID insertPendingRun(String scenarioId, UUID owner) {
        UUID id = UUID.randomUUID();
        runs.save(new SimulationRunRecord(id, UUID.fromString(scenarioId), owner, "test", 1, 0.5, 60,
                "{\"seed\":1,\"dtSeconds\":0.5,\"durationSeconds\":60,\"sampleIntervalSeconds\":5,"
                        + "\"saturationFlowPerLaneVph\":1800,\"stallThresholdSeconds\":300}",
                1, "{}", Instant.now()));
        return id;
    }
}
