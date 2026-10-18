package lab.simucity.app.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Ventana deslizante con un reloj controlado: sin esperas reales. */
class LoginThrottleTest {

    /** Reloj que se puede adelantar a mano. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-18T12:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private MutableClock clock;
    private LoginThrottle throttle;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        throttle = new LoginThrottle(
                new LoginThrottle.Settings(5, 20, Duration.ofMinutes(15), 3, Duration.ofHours(1)), clock);
    }

    @Test
    void cincoFallos_bloqueanLaCuenta_hastaQueCaducaElPrimero() {
        for (int i = 0; i < 4; i++) {
            throttle.loginFailed("ana@x.com", "1.1.1.1");
            clock.advance(Duration.ofMinutes(1));
        }
        assertThat(throttle.loginRetryAfterSeconds("ana@x.com", "1.1.1.1")).isZero();

        throttle.loginFailed("ana@x.com", "1.1.1.1"); // 5.º fallo en el minuto 4
        long retry = throttle.loginRetryAfterSeconds("ana@x.com", "1.1.1.1");
        assertThat(retry).isEqualTo(Duration.ofMinutes(11).toSeconds()); // el 1.º caduca en el minuto 15

        clock.advance(Duration.ofMinutes(11));
        assertThat(throttle.loginRetryAfterSeconds("ana@x.com", "1.1.1.1")).isZero();
    }

    @Test
    void elBloqueoEsPorCuenta_otraCuentaDesdeOtraIpNoSeVeAfectada() {
        for (int i = 0; i < 5; i++) {
            throttle.loginFailed("ana@x.com", "1.1.1.1");
        }
        assertThat(throttle.loginRetryAfterSeconds("ana@x.com", "9.9.9.9")).isPositive(); // la cuenta, desde cualquier IP
        assertThat(throttle.loginRetryAfterSeconds("beto@x.com", "9.9.9.9")).isZero();
    }

    @Test
    void muchosFallosDesdeUnaIp_bloqueanLaIp_aunqueSeanCuentasDistintas() {
        for (int i = 0; i < 20; i++) {
            throttle.loginFailed("usuario" + i + "@x.com", "6.6.6.6");
        }
        assertThat(throttle.loginRetryAfterSeconds("nuevo@x.com", "6.6.6.6")).isPositive();
        assertThat(throttle.loginRetryAfterSeconds("nuevo@x.com", "7.7.7.7")).isZero();
    }

    @Test
    void unLoginCorrecto_limpiaLosFallosDeLaCuenta() {
        for (int i = 0; i < 4; i++) {
            throttle.loginFailed("ana@x.com", "1.1.1.1");
        }
        throttle.loginSucceeded("ana@x.com");
        throttle.loginFailed("ana@x.com", "1.1.1.1");

        assertThat(throttle.loginRetryAfterSeconds("ana@x.com", "1.1.1.1")).isZero();
    }

    @Test
    void registrosPorIp_limitadosEnSuVentana() {
        for (int i = 0; i < 3; i++) {
            throttle.registered("5.5.5.5");
        }
        assertThat(throttle.registrationRetryAfterSeconds("5.5.5.5")).isPositive();
        assertThat(throttle.registrationRetryAfterSeconds("8.8.8.8")).isZero();

        clock.advance(Duration.ofHours(1).plusSeconds(1));
        assertThat(throttle.registrationRetryAfterSeconds("5.5.5.5")).isZero();
    }

    @Test
    void laLimpiezaPeriodicaBorraClavesCaducadas() {
        throttle.loginFailed("ana@x.com", "1.1.1.1");
        clock.advance(Duration.ofHours(2));
        throttle.purge();

        assertThat(throttle.loginRetryAfterSeconds("ana@x.com", "1.1.1.1")).isZero();
    }
}
