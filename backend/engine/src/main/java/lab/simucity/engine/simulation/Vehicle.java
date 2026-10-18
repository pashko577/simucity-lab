package lab.simucity.engine.simulation;

import java.util.List;

/** Vehículo dentro del motor. Estado mutable, solo visible dentro del paquete. */
final class Vehicle {

    final long id;
    final List<QueueSource> path;
    final long generatedTick;

    /** -1 = esperando en la entrada; i = dentro de {@code path.get(i)}. */
    int position = -1;
    /** Primer tick en que puede salir de su cola actual. */
    long readyTick;
    long waitTicks;

    Vehicle(long id, List<QueueSource> path, long generatedTick) {
        this.id = id;
        this.path = path;
        this.generatedTick = generatedTick;
        this.readyTick = generatedTick;
    }

    /** Siguiente calle, o {@code null} si la actual es la última (el siguiente paso es salir de la red). */
    QueueSource nextTarget() {
        return position + 1 < path.size() ? path.get(position + 1) : null;
    }
}
