package lab.simucity.engine.network;

import java.util.Objects;

/**
 * Vértice de la red.
 *
 * <p>Convención: coordenadas cartesianas con el eje Y hacia arriba y circulación por la derecha.
 * Si el editor usa coordenadas de pantalla (Y hacia abajo), debe invertir Y al construir la red;
 * de lo contrario la detección de conflictos semafóricos supondría circulación por la izquierda.
 *
 * @param id   identificador único dentro de la red
 * @param type rol del nodo
 * @param x    coordenada abstracta del editor (no geográfica)
 * @param y    coordenada abstracta del editor (no geográfica)
 */
public record Node(String id, NodeType type, double x, double y) {

    public Node {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }
}
