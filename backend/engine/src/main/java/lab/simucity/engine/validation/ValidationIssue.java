package lab.simucity.engine.validation;

/**
 * Problema detectado en la red o en los parámetros.
 *
 * @param severity  gravedad
 * @param code      código estable para la interfaz y las pruebas (p. ej. {@code EDGE_INVALID_LENGTH})
 * @param message   explicación en lenguaje sencillo
 * @param elementId nodo o arista afectada; {@code null} si aplica a toda la red
 */
public record ValidationIssue(Severity severity, String code, String message, String elementId) {
}
