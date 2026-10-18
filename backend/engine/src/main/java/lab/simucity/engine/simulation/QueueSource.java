package lab.simucity.engine.simulation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import lab.simucity.engine.network.Edge;

/**
 * Cola FIFO desde la que salen vehículos: una calle, o la cola de ingreso hacia la primera calle de
 * una ruta. La descarga está limitada por un cubo de fichas que se recarga al flujo de saturación,
 * de modo que un vehículo solo pasa sin demora y una cola sale al ritmo de saturación.
 */
final class QueueSource {

    final String id;
    final boolean entry;
    /** La propia calle, o la calle a la que alimenta esta entrada. */
    final Edge edge;
    /** Ticks para recorrer la calle a flujo libre (0 en entradas). */
    final long travelTicks;
    final double dischargePerTick;
    final double tokenCap;

    final ArrayDeque<Vehicle> vehicles = new ArrayDeque<>();
    double tokens;

    // Estado del tick en curso
    final List<Candidate> candidates = new ArrayList<>();
    int acceptedCount;
    int currentQueue;

    // Estadísticas
    long discharged;
    long queueSumTicks;
    int maxQueue;
    long stalledSinceTick = -1;
    boolean stallReported;

    private QueueSource(String id, boolean entry, Edge edge, long travelTicks, double dischargePerTick) {
        this.id = id;
        this.entry = entry;
        this.edge = edge;
        this.travelTicks = travelTicks;
        this.dischargePerTick = dischargePerTick;
        this.tokenCap = Math.max(1.0, dischargePerTick);
        this.tokens = tokenCap;
    }

    static QueueSource forEdge(Edge edge, long travelTicks, double dischargePerTick) {
        return new QueueSource(edge.id(), false, edge, travelTicks, dischargePerTick);
    }

    static QueueSource forEntry(QueueSource firstEdge) {
        return new QueueSource(firstEdge.id, true, firstEdge.edge, 0, firstEdge.dischargePerTick);
    }

    int storageCapacity() {
        return edge.storageCapacity();
    }
}
