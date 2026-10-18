package lab.simucity.app.shared;

import java.util.List;

/**
 * Los datos son estructuralmente correctos pero no válidos para el dominio (p. ej. el validador del motor
 * encontró errores). Responde 400 con la lista de problemas en la propiedad {@code issues}.
 */
public class ValidationFailedException extends RuntimeException {

    private final transient List<?> issues;

    public ValidationFailedException(String message, List<?> issues) {
        super(message);
        this.issues = List.copyOf(issues);
    }

    public List<?> issues() {
        return issues;
    }
}
