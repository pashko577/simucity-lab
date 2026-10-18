package lab.simucity.app;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

/** Base de las pruebas de API: aplicación completa + PostgreSQL real + MockMvc. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "una-clave-segura-123";

    @Autowired
    protected MockMvc mvc;

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.local";
    }

    protected static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    protected void register(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    /**
     * Escenario válido: E --(100 m a 10 m/s)--> I --(200 m a 10 m/s)--> X, 1 veh/min periódico.
     * En 100 s genera exactamente 1 vehículo, que tarda 30 s.
     */
    protected static final String STRAIGHT_ROAD = """
            {
              "nodes": [
                {"id": "E", "type": "ENTRY", "x": 0, "y": 0},
                {"id": "I", "type": "INTERSECTION", "x": 100, "y": 0},
                {"id": "X", "type": "EXIT", "x": 300, "y": 0}
              ],
              "edges": [
                {"id": "e1", "from": "E", "to": "I", "lengthMeters": 100, "lanes": 1, "speedLimitMps": 10,
                 "storageCapacity": 10, "closed": false},
                {"id": "e2", "from": "I", "to": "X", "lengthMeters": 200, "lanes": 1, "speedLimitMps": 10,
                 "storageCapacity": 10, "closed": false}
              ],
              "demands": [
                {"id": "d1", "origin": "E", "destination": "X", "vehiclesPerMinute": 1, "mode": "PERIODIC"}
              ],
              "signals": [],
              "prohibitedMovements": []
            }
            """;

    protected String createProject(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/projects").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Proyecto de prueba\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    protected String createScenario(MockHttpSession session, String projectId, String definitionJson)
            throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/projects/" + projectId + "/scenarios").with(csrf())
                        .session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Escenario\",\"definition\":" + definitionJson + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    /** Registra un usuario nuevo, inicia sesión y devuelve la sesión para reutilizarla. */
    protected MockHttpSession registerAndLogin() throws Exception {
        String email = uniqueEmail();
        register(email);
        MvcResult login = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }
}
