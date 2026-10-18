package lab.simucity.app.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import lab.simucity.app.TestcontainersConfiguration;

/**
 * Seguridad con un servidor y un cliente HTTP reales, como un navegador, y con Spring Session JDBC activo.
 * MockMvc sustituye el mecanismo CSRF y transporta la sesión como objeto, así que solo aquí se comprueban:
 * el protocolo CSRF de verdad, la renovación del token al iniciar sesión, las sesiones guardadas en PostgreSQL
 * y las cabeceras de seguridad.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        // El servidor de prueba usa http://: un cliente no envía cookies Secure sin HTTPS. En producción es true
        // (lo comprueba AuthApiTest.cookieDeSesionSeguraPorDefecto).
        properties = "server.servlet.session.cookie.secure=false")
@Import(TestcontainersConfiguration.class)
class RealHttpSecurityTest {

    private static final String PASSWORD = "una-clave-segura-123";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbc;

    private CookieManager cookies;
    private HttpClient client;

    @BeforeEach
    void newBrowser() {
        cookies = new CookieManager();
        client = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    @Test
    void elValorDeLaCookieEnLaCabecera_permiteRegistrarse() throws Exception {
        HttpResponse<String> csrf = send(get("/api/v1/auth/csrf"));
        assertThat(csrf.statusCode()).isEqualTo(200);
        assertThat(csrf.body()).contains("\"headerName\":\"X-XSRF-TOKEN\"").contains("\"cookieName\":\"XSRF-TOKEN\"");

        assertThat(send(post("/api/v1/auth/register", credentials(newEmail()), xsrf())).statusCode()).isEqualTo(201);
    }

    @Test
    void tokenIncorrecto_403ConCuerpoYNoUn401Enganoso() throws Exception {
        send(get("/api/v1/auth/csrf"));

        HttpResponse<String> response = send(post("/api/v1/auth/register", credentials(newEmail()), "token-incorrecto"));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void sesionPersistenteEnPostgres_cookieSegura_yTokenCsrfRenovadoAlIniciarSesion() throws Exception {
        String email = newEmail();
        send(get("/api/v1/auth/csrf"));
        send(post("/api/v1/auth/register", credentials(email), xsrf()));
        String tokenBeforeLogin = xsrf();

        HttpResponse<String> login = send(post("/api/v1/auth/login", credentials(email), tokenBeforeLogin));
        assertThat(login.statusCode()).isEqualTo(200);

        // Cookie de sesión de Spring Session: HttpOnly y SameSite=Lax.
        String sessionCookie = login.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("SESSION=")).findFirst().orElseThrow();
        assertThat(sessionCookie).contains("HttpOnly").contains("SameSite=Lax");

        // La sesión vive en PostgreSQL, no en la memoria del servidor (sobrevive a reinicios).
        assertThat(jdbc.sql("SELECT count(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = :e").param("e", email)
                .query(Long.class).single()).isEqualTo(1);
        assertThat(send(get("/api/v1/auth/me")).statusCode()).isEqualTo(200);

        // B3: el login borra el token CSRF; csrf.spa() emite uno nuevo en la siguiente petición (aquí, /me).
        assertThat(login.headers().allValues("Set-Cookie")).anyMatch(c -> c.startsWith("XSRF-TOKEN=;"));
        assertThat(xsrf()).isNotEqualTo(tokenBeforeLogin);
        assertThat(send(post("/api/v1/projects", "{\"name\":\"x\"}", tokenBeforeLogin)).statusCode()).isEqualTo(403);
        assertThat(send(post("/api/v1/projects", "{\"name\":\"x\"}", xsrf())).statusCode()).isEqualTo(201);

        // Cerrar sesión la elimina de la base de datos.
        assertThat(send(post("/api/v1/auth/logout", "", xsrf())).statusCode()).isEqualTo(204);
        assertThat(jdbc.sql("SELECT count(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = :e").param("e", email)
                .query(Long.class).single()).isZero();
        assertThat(send(get("/api/v1/auth/me")).statusCode()).isEqualTo(401);
    }

    @Test
    void cabecerasDeSeguridadEnLaApi() throws Exception {
        HttpResponse<String> response = send(get("/api/v1/auth/csrf"));

        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(response.headers().firstValue("Content-Security-Policy"))
                .hasValue("default-src 'none'; frame-ancestors 'none'");
        assertThat(response.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }

    // --- Ayudantes ---

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest get(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    }

    private HttpRequest post(String path, String json, String csrfHeader) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfHeader)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    private String xsrf() {
        String value = xsrfOrNull();
        assertThat(value).as("cookie XSRF-TOKEN").isNotNull();
        return value;
    }

    private String xsrfOrNull() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN") && !c.getValue().isEmpty())
                .map(HttpCookie::getValue).findFirst().orElse(null);
    }

    private static String newEmail() {
        return "real-" + UUID.randomUUID() + "@test.local";
    }

    private static String credentials(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
    }
}
