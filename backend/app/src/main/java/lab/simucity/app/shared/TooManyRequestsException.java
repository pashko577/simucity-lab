package lab.simucity.app.shared;

/** Límite de uso alcanzado (p. ej. demasiadas ejecuciones simultáneas). Responde 429. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
