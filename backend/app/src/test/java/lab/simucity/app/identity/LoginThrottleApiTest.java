package lab.simucity.app.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;

import lab.simucity.app.ApiTestSupport;
import lab.simucity.app.audit.AuditLog;

/** Fuerza bruta (B2) y auditoría (sección 14.7) a través de la API. */
class LoginThrottleApiTest extends ApiTestSupport {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AuditLog audit;

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(credentials(email, password)));
    }

    @Test
    void cincoFallos_bloqueanLaCuenta_inclusoConLaContrasenaCorrecta() throws Exception {
        String email = uniqueEmail();
        register(email);
        for (int i = 0; i < 5; i++) {
            login(email, "contrasena-incorrecta").andExpect(status().isUnauthorized());
        }

        login(email, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Demasiados intentos fallidos")));

        // Otra cuenta no se ve afectada.
        String other = uniqueEmail();
        register(other);
        login(other, PASSWORD).andExpect(status().isOk());
    }

    /** Sin revelar si la cuenta existe: un email inexistente también se bloquea igual. */
    @Test
    void emailInexistente_tambienSeBloquea() throws Exception {
        String ghost = uniqueEmail();
        for (int i = 0; i < 5; i++) {
            login(ghost, "lo-que-sea-12345").andExpect(status().isUnauthorized());
        }
        login(ghost, "lo-que-sea-12345").andExpect(status().isTooManyRequests());
    }

    @Test
    void seAuditanRegistroLoginsYBorrados_sinGuardarElEmailNiLaContrasena() throws Exception {
        String email = uniqueEmail();
        register(email);
        login(email, "contrasena-incorrecta").andExpect(status().isUnauthorized());
        MockHttpSession session = registerAndLoginAs(email);
        String projectId = createProject(session);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/v1/projects/" + projectId).with(csrf()).session(session)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session)).andExpect(status().isNoContent());

        String userId = jdbc.sql("SELECT id::text FROM users WHERE email = :e").param("e", email).query(String.class).single();
        List<String> types = jdbc.sql("SELECT event_type FROM audit_events WHERE user_id = :u::uuid ORDER BY id")
                .param("u", userId).query(String.class).list();
        assertThat(types).containsExactly("REGISTER", "LOGIN_FAILURE", "LOGIN_SUCCESS", "PROJECT_DELETED", "LOGOUT");

        List<String> stored = jdbc.sql("SELECT coalesce(details, '') || coalesce(client_ip, '') FROM audit_events "
                + "WHERE user_id = :u::uuid").param("u", userId).query(String.class).list();
        assertThat(String.join(" ", stored)).doesNotContain(email).doesNotContain("contrasena").doesNotContain(PASSWORD);
    }

    @Test
    void laRetencionBorraEventosAntiguos() {
        jdbc.sql("INSERT INTO audit_events (occurred_at, event_type) VALUES (:at, 'LOGIN_FAILURE')")
                .param("at", Timestamp.from(Instant.now().minus(400, ChronoUnit.DAYS))).update();

        assertThat(audit.purgeExpired()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE occurred_at < now() - interval '180 days'")
                .query(Long.class).single()).isZero();
    }

    /** Inicia sesión con una cuenta ya registrada. */
    private MockHttpSession registerAndLoginAs(String email) throws Exception {
        return (MockHttpSession) login(email, PASSWORD).andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
}
