package lab.simucity.engine.network;

import java.util.Objects;

/**
 * Segmento dirigido entre dos nodos. Los sentidos opuestos se modelan como aristas distintas.
 * Los valores no se validan aquí: {@link lab.simucity.engine.validation.NetworkValidator} los revisa
 * y reporta errores legibles en lugar de lanzar excepciones.
 *
 * @param id                  identificador único dentro de la red
 * @param fromNodeId          nodo de origen
 * @param toNodeId            nodo de destino
 * @param lengthMeters        longitud en metros
 * @param lanes               número de carriles
 * @param speedLimitMps       velocidad de referencia en m/s
 * @param storageCapacity     vehículos que caben en el segmento (parámetro pedagógico)
 * @param closed              si la vía está cerrada al tráfico
 */
public record Edge(
        String id,
        String fromNodeId,
        String toNodeId,
        double lengthMeters,
        int lanes,
        double speedLimitMps,
        int storageCapacity,
        boolean closed) {

    public Edge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fromNodeId, "fromNodeId");
        Objects.requireNonNull(toNodeId, "toNodeId");
    }

    /** Tiempo de recorrido a flujo libre, en segundos: longitud / velocidad. */
    public double freeFlowTravelTimeSeconds() {
        return lengthMeters / speedLimitMps;
    }

    public Edge withClosed(boolean value) {
        return new Edge(id, fromNodeId, toNodeId, lengthMeters, lanes, speedLimitMps, storageCapacity, value);
    }
}
