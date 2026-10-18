package lab.simucity.engine.routing;

import java.util.List;

/**
 * Ruta encontrada entre dos nodos.
 *
 * @param edgeIds     aristas recorridas, en orden
 * @param costSeconds costo total en segundos (tiempo de recorrido a flujo libre)
 */
public record Route(List<String> edgeIds, double costSeconds) {

    public Route {
        edgeIds = List.copyOf(edgeIds);
    }
}
