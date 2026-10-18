package lab.simucity.app.shared;

/** La operación choca con el estado actual (p. ej. email ya registrado). Responde 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
