package lab.simucity.app.shared;

/**
 * Recurso inexistente <b>o</b> ajeno. Se usa para ambos casos a propósito: responder 403 a un
 * recurso ajeno revelaría que existe (protección IDOR/BOLA).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource) {
        super(resource + " no encontrado");
    }
}
