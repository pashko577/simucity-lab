package lab.simucity.engine.network;

import java.util.Objects;

/** Paso de una arista de entrada a una arista de salida a través de una intersección. */
public record Movement(String fromEdgeId, String toEdgeId) {

    public Movement {
        Objects.requireNonNull(fromEdgeId, "fromEdgeId");
        Objects.requireNonNull(toEdgeId, "toEdgeId");
    }
}
