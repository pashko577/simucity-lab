package lab.simucity.app.identity;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lab.simucity.app.identity.AuthDtos.CsrfResponse;
import lab.simucity.app.identity.AuthDtos.LoginRequest;
import lab.simucity.app.identity.AuthDtos.RegisterRequest;
import lab.simucity.app.identity.AuthDtos.UserResponse;

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

    AuthController(RegistrationService registration, AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository) {
        this.registration = registration;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
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
    UserResponse register(@Valid @RequestBody RegisterRequest body) {
        return UserResponse.from(registration.register(body.email(), body.password()));
    }

    @PostMapping("/login")
    UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        Authentication verified = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        DatabaseUserDetailsService.normalizeEmail(body.email()), body.password()));
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

        return UserResponse.from(principal);
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return UserResponse.from(principal);
    }
}
