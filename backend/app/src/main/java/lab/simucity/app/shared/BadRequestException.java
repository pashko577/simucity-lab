package lab.simucity.app.shared;

/** Datos inválidos que no cubren las anotaciones de validación. Responde 400 con el mensaje. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
