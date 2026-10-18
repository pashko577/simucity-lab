package lab.simucity.engine.simulation;

import lab.simucity.engine.validation.ValidationResult;

/** El escenario tiene errores de validación y no puede ejecutarse. Contiene la lista completa de problemas. */
public final class InvalidScenarioException extends RuntimeException {

    private final transient ValidationResult validation;

    public InvalidScenarioException(ValidationResult validation) {
        super("El escenario no es válido: " + validation.errors().size() + " error(es).");
        this.validation = validation;
    }

    public ValidationResult validation() {
        return validation;
    }
}
