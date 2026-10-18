package lab.simucity.engine.simulation;

import java.util.List;
import java.util.Objects;

/**
 * Flujo de vehículos de un origen a un destino.
 *
 * @param id                 identificador único dentro del escenario
 * @param originNodeId       nodo de tipo ENTRY
 * @param destinationNodeId  nodo de tipo EXIT
 * @param vehiclesPerMinute  tasa base de llegada, en veh/min
 * @param mode               forma de generar las llegadas
 * @param profile            periodos con tasa distinta a la base; no deben solaparse. Vacío = tasa constante
 */
public record DemandSpec(
        String id,
        String originNodeId,
        String destinationNodeId,
        double vehiclesPerMinute,
        ArrivalMode mode,
        List<DemandPeriod> profile) {

    public DemandSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(originNodeId, "originNodeId");
        Objects.requireNonNull(destinationNodeId, "destinationNodeId");
        Objects.requireNonNull(mode, "mode");
        profile = List.copyOf(profile);
    }

    /** Demanda de tasa constante. */
    public DemandSpec(String id, String originNodeId, String destinationNodeId, double vehiclesPerMinute,
            ArrivalMode mode) {
        this(id, originNodeId, destinationNodeId, vehiclesPerMinute, mode, List.of());
    }

    /**
     * Vehículos esperados desde el segundo 0 hasta {@code seconds}: la integral de la tasa.
     * Se calcula desde cero en cada llamada (sin acumular decimales entre ticks).
     */
    public double expectedVehiclesUntil(double seconds) {
        double total = vehiclesPerMinute * seconds;
        for (DemandPeriod period : profile) {
            double overlap = Math.min(period.toSeconds(), seconds) - period.fromSeconds();
            if (overlap > 0) {
                total += (period.vehiclesPerMinute() - vehiclesPerMinute) * overlap;
            }
        }
        return total / 60.0;
    }
}
