package lab.simucity.app.scenarios;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lab.simucity.engine.network.Edge;
import lab.simucity.engine.network.Movement;
import lab.simucity.engine.network.Network;
import lab.simucity.engine.network.Node;
import lab.simucity.engine.network.NodeType;
import lab.simucity.engine.simulation.ArrivalMode;
import lab.simucity.engine.simulation.DemandPeriod;
import lab.simucity.engine.simulation.DemandSpec;
import lab.simucity.engine.simulation.Scenario;
import lab.simucity.engine.simulation.SignalPhase;
import lab.simucity.engine.simulation.SignalPlan;

/**
 * Formato JSON de un escenario (versión de esquema {@link #SCHEMA_VERSION}). Es lo que envía el editor y lo
 * que se guarda en {@code scenarios.definition} y en el snapshot de cada ejecución.
 *
 * <p>Aquí solo se valida la <b>estructura</b> (campos presentes, tamaños). Las reglas del dominio (longitudes
 * positivas, conexiones, conflictos semafóricos...) las valida el motor con mensajes propios.
 */
public record ScenarioDefinition(
        @NotNull @Size(max = 500) List<@Valid @NotNull NodeDef> nodes,
        @NotNull @Size(max = 2000) List<@Valid @NotNull EdgeDef> edges,
        @NotNull @Size(max = 200) List<@Valid @NotNull DemandDef> demands,
        @NotNull @Size(max = 500) List<@Valid @NotNull SignalDef> signals,
        @NotNull @Size(max = 2000) List<@Valid @NotNull MovementDef> prohibitedMovements) {

    /** Incrementar al cambiar el formato de forma incompatible (y migrar o convertir los guardados). */
    public static final int SCHEMA_VERSION = 1;

    public static ScenarioDefinition empty() {
        return new ScenarioDefinition(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public record NodeDef(
            @NotBlank @Size(max = 64) String id,
            @NotNull NodeType type,
            @NotNull Double x,
            @NotNull Double y) {
    }

    public record EdgeDef(
            @NotBlank @Size(max = 64) String id,
            @NotBlank @Size(max = 64) String from,
            @NotBlank @Size(max = 64) String to,
            @NotNull Double lengthMeters,
            @NotNull Integer lanes,
            @NotNull Double speedLimitMps,
            @NotNull Integer storageCapacity,
            boolean closed) {
    }

    public record DemandDef(
            @NotBlank @Size(max = 64) String id,
            @NotBlank @Size(max = 64) String origin,
            @NotBlank @Size(max = 64) String destination,
            @NotNull Double vehiclesPerMinute,
            @NotNull ArrivalMode mode,
            @Size(max = 24) List<@Valid @NotNull PeriodDef> profile) {
    }

    public record PeriodDef(@NotNull Double fromSeconds, @NotNull Double toSeconds, @NotNull Double vehiclesPerMinute) {
    }

    public record SignalDef(
            @NotBlank @Size(max = 64) String nodeId,
            @NotNull Double offsetSeconds,
            @NotNull @Size(max = 16) List<@Valid @NotNull PhaseDef> phases) {
    }

    public record PhaseDef(
            @NotNull Double durationSeconds,
            @NotNull @Size(max = 64) List<@Valid @NotNull MovementDef> greenMovements) {
    }

    public record MovementDef(@NotBlank @Size(max = 64) String from, @NotBlank @Size(max = 64) String to) {

        Movement toEngine() {
            return new Movement(from, to);
        }
    }

    /** Convierte al modelo del motor. Requiere una definición que ya pasó la validación de estructura. */
    public Scenario toEngine() {
        List<Node> engineNodes = nodes.stream()
                .map(n -> new Node(n.id(), n.type(), n.x(), n.y()))
                .toList();
        List<Edge> engineEdges = edges.stream()
                .map(e -> new Edge(e.id(), e.from(), e.to(), e.lengthMeters(), e.lanes(), e.speedLimitMps(),
                        e.storageCapacity(), e.closed()))
                .toList();
        List<DemandSpec> engineDemands = demands.stream()
                .map(d -> new DemandSpec(d.id(), d.origin(), d.destination(), d.vehiclesPerMinute(), d.mode(),
                        d.profile() == null ? List.of() : d.profile().stream()
                                .map(p -> new DemandPeriod(p.fromSeconds(), p.toSeconds(), p.vehiclesPerMinute()))
                                .toList()))
                .toList();
        List<SignalPlan> engineSignals = signals.stream()
                .map(s -> new SignalPlan(s.nodeId(), s.offsetSeconds(), s.phases().stream()
                        .map(p -> new SignalPhase(p.durationSeconds(),
                                p.greenMovements().stream().map(MovementDef::toEngine).toList()))
                        .toList()))
                .toList();
        List<Movement> prohibited = prohibitedMovements.stream().map(MovementDef::toEngine).toList();

        return new Scenario(new Network(engineNodes, engineEdges), engineDemands, engineSignals, prohibited);
    }
}
