package lab.simucity.app.identity;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contratos de entrada y salida del módulo de identidad. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 128, message = "La contraseña debe tener entre 12 y 128 caracteres.")
            String password) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {
    }

    public record UserResponse(UUID id, String email) {

        static UserResponse from(AppUserPrincipal principal) {
            return new UserResponse(principal.id(), principal.email());
        }

        static UserResponse from(UserAccount user) {
            return new UserResponse(user.getId(), user.getEmail());
        }
    }

    /** Copiar el valor de la cookie {@code cookieName} en la cabecera {@code headerName}. */
    public record CsrfResponse(String headerName, String cookieName) {
    }
}
