package lab.simucity.app;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Retención de auditoría y limpieza del limitador de intentos.
public class SimuCityApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimuCityApplication.class, args);
    }

    /**
     * Reloj inyectable (las pruebas pueden fijar el tiempo). Avanza en microsegundos, la precisión de
     * TIMESTAMPTZ en PostgreSQL: así lo que devuelve la API al crear coincide con lo que se lee después.
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
