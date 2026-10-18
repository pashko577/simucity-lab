package lab.simucity.engine.simulation;

/** Movimiento propuesto en un tick, antes de resolver la capacidad. */
final class Candidate {

    final Vehicle vehicle;
    /** Calle destino, o {@code null} si el vehículo sale de la red. */
    final QueueSource target;
    boolean accepted;

    Candidate(Vehicle vehicle, QueueSource target) {
        this.vehicle = vehicle;
        this.target = target;
    }
}
