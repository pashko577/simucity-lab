package lab.simucity.app.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import lab.simucity.app.TestcontainersConfiguration;

/**
 * CSRF con un servidor y un cliente HTTP reales, como un navegador. MockMvc sustituye el repositorio CSRF por
 * uno de prueba, así que solo aquí se comprueba el protocolo de verdad: copiar la cookie XSRF-TOKEN en la
 * cabecera X-XSRF-TOKEN.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CsrfRealHttpTest {

    @LocalServerPort
    private int port;

    @Test
    void elValorDeLaCookieEnLaCabecera_permiteRegistrarse() throws Exception {
        CookieManager cookies = new CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

        HttpResponse<String> csrf = client.send(get("/api/v1/auth/csrf"), HttpResponse.BodyHandlers.ofString());
        assertThat(csrf.statusCode()).isEqualTo(200);
        assertThat(csrf.body()).contains("\"headerName\":\"X-XSRF-TOKEN\"").contains("\"cookieName\":\"XSRF-TOKEN\"");
        String cookieValue = cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN")).map(HttpCookie::getValue).findFirst().orElseThrow();

        HttpResponse<String> register = client.send(register(cookieValue), HttpResponse.BodyHandlers.ofString());

        assertThat(register.statusCode()).isEqualTo(201);
    }

    @Test
    void tokenIncorrecto_403ConCuerpoYNoUn401Enganoso() throws Exception {
        CookieManager cookies = new CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        client.send(get("/api/v1/auth/csrf"), HttpResponse.BodyHandlers.ofString());

        HttpResponse<String> register = client.send(register("token-incorrecto"), HttpResponse.BodyHandlers.ofString());

        assertThat(register.statusCode()).isEqualTo(403);
    }

    private HttpRequest get(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    }

    private HttpRequest register(String csrfHeader) {
        String body = "{\"email\":\"real-" + UUID.randomUUID() + "@test.local\",\"password\":\"una-clave-segura-123\"}";
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfHeader)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }
}
