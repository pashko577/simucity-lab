package lab.simucity.app.identity;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lab.simucity.app.audit.AuditEventType;
import lab.simucity.app.audit.AuditLog;
import lab.simucity.app.identity.AuthDtos.CsrfResponse;
import lab.simucity.app.identity.AuthDtos.LoginRequest;
import lab.simucity.app.identity.AuthDtos.RegisterRequest;
import lab.simucity.app.identity.AuthDtos.UserResponse;
import lab.simucity.app.shared.RateLimitedException;

/**
 * Registro e inicio de sesión con cookie de sesión (HttpOnly, SameSite=Lax) y protección CSRF.
 * El cierre de sesión lo atiende Spring Security en POST /api/v1/auth/logout.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final RegistrationService registration;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final LoginThrottle throttle;
    private final UserAccountRepository users;
    private final AuditLog audit;

    AuthController(RegistrationService registration, AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository,
            LoginThrottle throttle, UserAccountRepository users, AuditLog audit) {
        this.registration = registration;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.throttle = throttle;
        this.users = users;
        this.audit = audit;
    }

    /**
     * Garantiza que exista la cookie {@code XSRF-TOKEN} e indica dónde enviarla. El frontend debe copiar el
     * <b>valor de la cookie</b> en la cabecera: {@code csrf.spa()} compara la cabecera con el token crudo.
     * No se devuelve {@code token.getToken()}: es la versión enmascarada (BREACH), válida solo como parámetro
     * de formulario, y usarla en la cabecera produce un 403.
     */
    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        token.getToken(); // Acceder al token diferido hace que se escriba la cookie.
        return new CsrfResponse(token.getHeaderName(), "XSRF-TOKEN");
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        long retryAfter = throttle.registrationRetryAfterSeconds(ip);
        if (retryAfter > 0) {
            audit.record(AuditEventType.REGISTER_BLOCKED, null, ip, null);
            throw new RateLimitedException("Demasiadas cuentas creadas desde esta conexión. Inténtalo más tarde.",
                    retryAfter);
        }
        UserAccount user = registration.register(body.email(), body.password());
        throttle.registered(ip);
        audit.record(AuditEventType.REGISTER, user.getId(), ip, null);
        return UserResponse.from(user);
    }

    @PostMapping("/login")
    UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        String email = DatabaseUserDetailsService.normalizeEmail(body.email());
        String ip = request.getRemoteAddr();

        // Fuerza bruta (B2): se comprueba ANTES de verificar la contraseña, exista o no la cuenta.
        long retryAfter = throttle.loginRetryAfterSeconds(email, ip);
        if (retryAfter > 0) {
            audit.record(AuditEventType.LOGIN_BLOCKED, userIdOf(email), ip, null);
            throw new RateLimitedException("Demasiados intentos fallidos. Espera "
                    + Math.max(1, Math.round(retryAfter / 60.0)) + " min antes de volver a intentarlo.", retryAfter);
        }

        Authentication verified;
        try {
            verified = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, body.password()));
        } catch (AuthenticationException failed) {
            throttle.loginFailed(email, ip);
            audit.record(AuditEventType.LOGIN_FAILURE, userIdOf(email), ip, null);
            throw failed; // ApiExceptionHandler: 401 "Credenciales inválidas."
        }
        throttle.loginSucceeded(email);

        AppUserPrincipal principal = ((AppUserPrincipal) verified.getPrincipal()).withoutPassword();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());

        // Fijación de sesión: un ID de sesión previo al login nunca sigue siendo válido después.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        // B3: el token CSRF de antes del login deja de valer; el frontend pedirá uno nuevo.
        csrfTokenRepository.saveToken(null, request, response);

        audit.record(AuditEventType.LOGIN_SUCCESS, principal.id(), ip, null);
        return UserResponse.from(principal);
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return UserResponse.from(principal);
    }

    private UUID userIdOf(String email) {
        return users.findByEmail(email).map(UserAccount::getId).orElse(null);
    }
}
