package lab.simucity.engine.simulation;

import java.util.List;

/**
 * Fotografía del estado en un instante; es lo que la animación reproduce.
 *
 * @param tick                paso de simulación
 * @param timeSeconds         tiempo simulado
 * @param vehiclesInNetwork   vehículos dentro de las calles
 * @param queuedVehicles      vehículos en cola dentro de las calles (listos para avanzar pero detenidos)
 * @param waitingAtEntries    vehículos que aún no pudieron ingresar a la red
 * @param completedTrips      viajes completados hasta este instante
 * @param edges               estado por calle, ordenado por ID
 */
public record Sample(
        long tick,
        double timeSeconds,
        int vehiclesInNetwork,
        int queuedVehicles,
        int waitingAtEntries,
        long completedTrips,
        List<EdgeSample> edges) {

    public Sample {
        edges = List.copyOf(edges);
    }

    public record EdgeSample(String edgeId, int vehicles, int queued) {
    }
}
