package lab.simucity.engine.network;

public enum NodeType {
    /** Cruce entre segmentos; puede tener semáforo. */
    INTERSECTION,
    /** Punto donde los vehículos ingresan a la red. */
    ENTRY,
    /** Punto donde los vehículos salen de la red (destino). */
    EXIT,
    /** Punto de conexión geométrica sin reglas de cruce. */
    CONNECTOR
}
