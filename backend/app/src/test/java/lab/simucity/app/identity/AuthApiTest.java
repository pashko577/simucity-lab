package lab.simucity.app.identity;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import lab.simucity.app.ApiTestSupport;

class AuthApiTest extends ApiTestSupport {

    @Test
    void registroLoginMeYLogout() throws Exception {
        MockHttpSession session = registerAndLogin();

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(content().string(not(containsString("password"))));

        mvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinSesion_401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void emailDuplicado_sinImportarMayusculas_409() throws Exception {
        String email = uniqueEmail();
        register(email);

        mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void loginConEmailEnMayusculas_funciona() throws Exception {
        String email = uniqueEmail();
        register(email);

        mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void credencialesIncorrectas_mismoMensajeExistaONoElEmail() throws Exception {
        String email = uniqueEmail();
        register(email);

        mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email, "otra-clave-incorrecta")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas."));

        mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(uniqueEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas."));
    }

    @Test
    void contrasenaCorta_400ConDetalleDelCampo() throws Exception {
        mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(uniqueEmail(), "corta")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void contrasenaDeMasDe72Bytes_400() throws Exception {
        String longPassword = "ñ".repeat(40); // 40 caracteres, 80 bytes en UTF-8

        mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(uniqueEmail(), longPassword)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void peticionMutanteSinCsrf_403() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(uniqueEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
    }

    /**
     * MockMvc + spring-security-test sustituye el repositorio CSRF por uno de prueba, así que aquí solo se
     * comprueba que el endpoint es público y entrega un token. La cabecera real (X-XSRF-TOKEN) y la cookie
     * XSRF-TOKEN se verificaron contra la aplicación en ejecución (ver docs/arquitectura-backend.md).
     */
    @Test
    void endpointCsrf_esPublicoYEntregaToken() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").isNotEmpty())
                .andExpect(jsonPath("$.cookieName").value("XSRF-TOKEN"));
    }

    @Test
    void cookieDeSesionSeguraPorDefecto(@org.springframework.beans.factory.annotation.Autowired
            org.springframework.core.env.Environment env) {
        org.assertj.core.api.Assertions.assertThat(env.getProperty("server.servlet.session.cookie.secure"))
                .isEqualTo("true");
        org.assertj.core.api.Assertions.assertThat(env.getProperty("server.servlet.session.cookie.http-only"))
                .isEqualTo("true");
    }

    @Test
    void healthCheck_esPublicoYNoDaDetalles() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}
