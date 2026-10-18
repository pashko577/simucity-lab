package lab.simucity.app.identity;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lab.simucity.app.shared.BadRequestException;
import lab.simucity.app.shared.ConflictException;

@Service
public class RegistrationService {

    /** BCrypt solo considera los primeros 72 bytes; rechazar en vez de truncar en silencio. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    RegistrationService(UserAccountRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public UserAccount register(String rawEmail, String rawPassword) {
        String email = DatabaseUserDetailsService.normalizeEmail(rawEmail);
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BadRequestException("La contraseña es demasiado larga (máximo 72 bytes).");
        }
        if (users.existsByEmail(email)) {
            throw new ConflictException("Ya existe una cuenta con ese email.");
        }
        UserAccount user = new UserAccount(UUID.randomUUID(), email, passwordEncoder.encode(rawPassword),
                clock.instant());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException raceWithConcurrentRegistration) {
            throw new ConflictException("Ya existe una cuenta con ese email.");
        }
    }
}
