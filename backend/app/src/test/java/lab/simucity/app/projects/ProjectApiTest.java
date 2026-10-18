package lab.simucity.app.projects;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import lab.simucity.app.ApiTestSupport;

class ProjectApiTest extends ApiTestSupport {

    @Test
    void cicloCompleto_crearListarObtenerActualizarEliminar() throws Exception {
        MockHttpSession ana = registerAndLogin();
        String id = createProject(ana, "Centro histórico");

        mvc.perform(get("/api/v1/projects").session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Centro histórico"));

        mvc.perform(patch("/api/v1/projects/" + id).with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Cierre de la Av. Lima\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Centro histórico"))
                .andExpect(jsonPath("$.description").value("Cierre de la Av. Lima"));

        mvc.perform(delete("/api/v1/projects/" + id).with(csrf()).session(ana))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/projects/" + id).session(ana))
                .andExpect(status().isNotFound());
    }

    /** IDOR/BOLA: cambiar el ID en la URL no da acceso a proyectos ajenos (sección 14.2). */
    @Test
    void otroUsuario_noPuedeVerNiModificarNiBorrarProyectosAjenos() throws Exception {
        MockHttpSession ana = registerAndLogin();
        MockHttpSession beto = registerAndLogin();
        String anasProject = createProject(ana, "Proyecto de Ana");

        mvc.perform(get("/api/v1/projects/" + anasProject).session(beto))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/projects/" + anasProject).with(csrf()).session(beto)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hackeado\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/projects/" + anasProject).with(csrf()).session(beto))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects").session(beto))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/v1/projects/" + anasProject).session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Proyecto de Ana"));
    }

    @Test
    void sinSesion_401() throws Exception {
        mvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
    }

    @Test
    void nombreVacioOMuyLargo_400() throws Exception {
        MockHttpSession ana = registerAndLogin();

        mvc.perform(post("/api/v1/projects").with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        mvc.perform(post("/api/v1/projects").with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + "x".repeat(121) + "\"}"))
                .andExpect(status().isBadRequest());

        String id = createProject(ana, "Válido");
        mvc.perform(patch("/api/v1/projects/" + id).with(csrf()).session(ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void idMalFormado_400SinDetallesInternos() throws Exception {
        MockHttpSession ana = registerAndLogin();

        mvc.perform(get("/api/v1/projects/no-es-un-uuid").session(ana))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("trace"))));
    }

    @Test
    void tamanoDePaginaExcesivo_seLimitaA100() throws Exception {
        MockHttpSession ana = registerAndLogin();

        mvc.perform(get("/api/v1/projects").param("size", "100000").session(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        mvc.perform(get("/api/v1/projects").param("page", "-1").session(ana))
                .andExpect(status().isBadRequest());
    }

    private String createProject(MockHttpSession session, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/projects").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
