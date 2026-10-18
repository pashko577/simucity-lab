package lab.simucity.engine.validation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import lab.simucity.engine.network.Edge;
import lab.simucity.engine.network.Movement;
import lab.simucity.engine.network.MovementRules;
import lab.simucity.engine.network.Network;
import lab.simucity.engine.network.Node;
import lab.simucity.engine.network.NodeType;
import lab.simucity.engine.routing.DijkstraRouter;
import lab.simucity.engine.simulation.DemandPeriod;
import lab.simucity.engine.simulation.DemandSpec;
import lab.simucity.engine.simulation.RunParameters;
import lab.simucity.engine.simulation.Scenario;
import lab.simucity.engine.simulation.SignalPhase;
import lab.simucity.engine.simulation.SignalPlan;

/**
 * Valida red, parámetros, demanda y semáforos antes de ejecutar.
 * Nunca lanza excepciones por datos inválidos: devuelve errores y advertencias.
 */
public final class ScenarioValidator {

    /** Límites para evitar ejecuciones excesivas (sección 14.5 de la especificación). */
    public record Limits(
            double minDtSeconds,
            double maxDtSeconds,
            double maxDurationSeconds,
            double maxDemandPerMinute,
            long maxExpectedVehicles,
            long maxSampleCells) {

        public static final Limits MVP_DEFAULT = new Limits(0.1, 5.0, 21_600, 120, 200_000, 2_000_000);
    }

    private final NetworkValidator networkValidator;
    private final Limits limits;
    private final DijkstraRouter router = new DijkstraRouter();

    public ScenarioValidator() {
        this(new NetworkValidator(), Limits.MVP_DEFAULT);
    }

    public ScenarioValidator(NetworkValidator networkValidator, Limits limits) {
        this.networkValidator = networkValidator;
        this.limits = limits;
    }

    public ValidationResult validate(Scenario scenario, RunParameters params) {
        List<ValidationIssue> issues = new ArrayList<>(networkValidator.validate(scenario.network()).issues());
        boolean networkValid = issues.stream().noneMatch(i -> i.severity() == Severity.ERROR);

        boolean paramsValid = checkParameters(params, scenario.network(), issues);
        boolean movementsValid = checkProhibitedMovements(scenario, issues);
        checkDemands(scenario, params, paramsValid, networkValid && movementsValid, issues);
        checkSignals(scenario, networkValid, issues);
        return new ValidationResult(issues);
    }

    private static boolean checkProhibitedMovements(Scenario scenario, List<ValidationIssue> issues) {
        boolean valid = true;
        for (Movement m : scenario.prohibitedMovements()) {
            if (!passesThroughNode(scenario.network(), m)) {
                issues.add(error("MOVEMENT_INVALID", "El giro prohibido " + m.fromEdgeId() + " → " + m.toEdgeId()
                        + " no conecta dos calles en una intersección.", m.fromEdgeId()));
                valid = false;
            }
        }
        return valid;
    }

    private static boolean passesThroughNode(Network network, Movement m) {
        Optional<Edge> from = network.edge(m.fromEdgeId());
        Optional<Edge> to = network.edge(m.toEdgeId());
        return from.isPresent() && to.isPresent() && from.get().toNodeId().equals(to.get().fromNodeId());
    }

    private boolean checkParameters(RunParameters p, Network network, List<ValidationIssue> issues) {
        int before = issues.size();
        boolean dtValid = Double.isFinite(p.dtSeconds())
                && p.dtSeconds() >= limits.minDtSeconds() && p.dtSeconds() <= limits.maxDtSeconds();
        if (!dtValid) {
            issues.add(error("PARAM_INVALID_DT", "El paso temporal debe estar entre "
                    + limits.minDtSeconds() + " y " + limits.maxDtSeconds() + " s.", null));
        }
        if (!Double.isFinite(p.durationSeconds()) || p.durationSeconds() <= 0
                || p.durationSeconds() > limits.maxDurationSeconds()
                || (dtValid && p.durationSeconds() < p.dtSeconds())) {
            issues.add(error("PARAM_INVALID_DURATION", "La duración debe ser mayor que el paso temporal y como máximo "
                    + limits.maxDurationSeconds() + " s.", null));
        }
        if (!Double.isFinite(p.sampleIntervalSeconds()) || p.sampleIntervalSeconds() <= 0
                || (dtValid && p.sampleIntervalSeconds() < p.dtSeconds())) {
            issues.add(error("PARAM_INVALID_SAMPLE_INTERVAL",
                    "El intervalo de muestreo debe ser mayor o igual que el paso temporal.", null));
        }
        if (!isPositiveFinite(p.saturationFlowPerLaneVph()) || p.saturationFlowPerLaneVph() > 3600) {
            issues.add(error("PARAM_INVALID_SATURATION_FLOW",
                    "El flujo de saturación debe estar entre 0 y 3600 veh/h por carril.", null));
        }
        if (!isPositiveFinite(p.stallThresholdSeconds())) {
            issues.add(error("PARAM_INVALID_STALL_THRESHOLD",
                    "El umbral de bloqueo debe ser un número mayor que 0 s.", null));
        }
        boolean valid = issues.size() == before;
        if (valid) {
            long samples = p.durationTicks() / p.sampleEveryTicks() + 1;
            long cells = samples * Math.max(1, network.edges().size());
            if (cells > limits.maxSampleCells()) {
                issues.add(error("SAMPLES_TOO_MANY",
                        "Demasiadas muestras para esta red; aumente el intervalo de muestreo.", null));
                valid = false;
            }
        }
        return valid;
    }

    private void checkDemands(Scenario scenario, RunParameters p, boolean paramsValid, boolean networkValid,
            List<ValidationIssue> issues) {
        Network network = scenario.network();
        Set<String> ids = new HashSet<>();
        double expectedVehicles = 0;

        for (DemandSpec d : scenario.demands()) {
            String id = d.id();
            if (!ids.add(id)) {
                issues.add(error("DEMAND_DUPLICATE_ID", "Hay más de una demanda con el ID '" + id + "'.", id));
            }
            if (!Double.isFinite(d.vehiclesPerMinute()) || d.vehiclesPerMinute() < 0
                    || d.vehiclesPerMinute() > limits.maxDemandPerMinute()) {
                issues.add(error("DEMAND_INVALID_RATE", "La demanda '" + id + "' debe estar entre 0 y "
                        + limits.maxDemandPerMinute() + " veh/min.", id));
            }
            boolean profileValid = checkProfile(d, issues);
            if (profileValid && Double.isFinite(d.vehiclesPerMinute()) && d.vehiclesPerMinute() >= 0) {
                expectedVehicles += d.expectedVehiclesUntil(p.durationSeconds());
            }

            boolean endpointsValid = true;
            Optional<Node> origin = network.node(d.originNodeId());
            if (origin.isEmpty()) {
                issues.add(error("DEMAND_UNKNOWN_ORIGIN",
                        "El origen '" + d.originNodeId() + "' de la demanda '" + id + "' no existe.", id));
                endpointsValid = false;
            } else if (origin.get().type() != NodeType.ENTRY) {
                issues.add(error("DEMAND_ORIGIN_NOT_ENTRY",
                        "El origen de la demanda '" + id + "' debe ser un nodo de entrada.", id));
                endpointsValid = false;
            }
            Optional<Node> destination = network.node(d.destinationNodeId());
            if (destination.isEmpty()) {
                issues.add(error("DEMAND_UNKNOWN_DESTINATION",
                        "El destino '" + d.destinationNodeId() + "' de la demanda '" + id + "' no existe.", id));
                endpointsValid = false;
            } else if (destination.get().type() != NodeType.EXIT) {
                issues.add(error("DEMAND_DESTINATION_NOT_EXIT",
                        "El destino de la demanda '" + id + "' debe ser un nodo de salida.", id));
                endpointsValid = false;
            }
            if (d.originNodeId().equals(d.destinationNodeId())) {
                issues.add(error("DEMAND_SAME_ORIGIN_DESTINATION",
                        "La demanda '" + id + "' tiene el mismo origen y destino.", id));
                endpointsValid = false;
            }

            if (networkValid && endpointsValid && router.findRoute(network, d.originNodeId(),
                    d.destinationNodeId(), scenario.movementRules()).isEmpty()) {
                issues.add(warning("DEMAND_NO_ROUTE", "No hay ruta abierta para la demanda '" + id
                        + "'; no se generarán sus vehículos.", id));
            }
        }

        if (paramsValid && expectedVehicles > limits.maxExpectedVehicles()) {
            issues.add(error("DEMAND_TOO_MANY_VEHICLES", "La demanda total generaría unos "
                    + Math.round(expectedVehicles) + " vehículos; el máximo es " + limits.maxExpectedVehicles() + ".",
                    null));
        }
    }

    /** Periodos con valores válidos y sin solaparse. */
    private boolean checkProfile(DemandSpec d, List<ValidationIssue> issues) {
        boolean valid = true;
        for (DemandPeriod period : d.profile()) {
            boolean bounds = Double.isFinite(period.fromSeconds()) && Double.isFinite(period.toSeconds())
                    && period.fromSeconds() >= 0 && period.toSeconds() > period.fromSeconds();
            boolean rate = Double.isFinite(period.vehiclesPerMinute()) && period.vehiclesPerMinute() >= 0
                    && period.vehiclesPerMinute() <= limits.maxDemandPerMinute();
            if (!bounds || !rate) {
                issues.add(error("DEMAND_INVALID_PERIOD", "La demanda '" + d.id() + "' tiene un periodo inválido: "
                        + "el fin debe ser posterior al inicio y la tasa estar entre 0 y "
                        + limits.maxDemandPerMinute() + " veh/min.", d.id()));
                valid = false;
            }
        }
        if (!valid) {
            return false;
        }
        List<DemandPeriod> sorted = d.profile().stream()
                .sorted(Comparator.comparingDouble(DemandPeriod::fromSeconds))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).fromSeconds() < sorted.get(i - 1).toSeconds()) {
                issues.add(error("DEMAND_OVERLAPPING_PERIODS",
                        "La demanda '" + d.id() + "' tiene periodos que se solapan.", d.id()));
                return false;
            }
        }
        return true;
    }

    private static void checkSignals(Scenario scenario, boolean networkValid, List<ValidationIssue> issues) {
        Network network = scenario.network();
        MovementRules rules = scenario.movementRules();
        Set<String> controlled = new HashSet<>();

        for (SignalPlan plan : scenario.signals()) {
            String nodeId = plan.nodeId();
            if (!controlled.add(nodeId)) {
                issues.add(error("SIGNAL_DUPLICATE_NODE",
                        "La intersección '" + nodeId + "' tiene más de un plan semafórico.", nodeId));
            }
            if (network.node(nodeId).isEmpty()) {
                issues.add(error("SIGNAL_UNKNOWN_NODE",
                        "El semáforo apunta a una intersección inexistente ('" + nodeId + "').", nodeId));
                continue;
            }
            if (!Double.isFinite(plan.offsetSeconds()) || plan.offsetSeconds() < 0) {
                issues.add(error("SIGNAL_INVALID_OFFSET",
                        "El desfase del semáforo en '" + nodeId + "' debe ser mayor o igual que 0 s.", nodeId));
            }
            if (plan.phases().isEmpty()) {
                issues.add(error("SIGNAL_NO_PHASES", "El semáforo en '" + nodeId + "' no tiene fases.", nodeId));
            }
            for (int p = 0; p < plan.phases().size(); p++) {
                SignalPhase phase = plan.phases().get(p);
                if (!isPositiveFinite(phase.durationSeconds())) {
                    issues.add(error("SIGNAL_INVALID_PHASE_DURATION",
                            "Cada fase del semáforo en '" + nodeId + "' debe durar más de 0 s.", nodeId));
                }
                boolean movementsValid = true;
                for (Movement m : phase.greenMovements()) {
                    Optional<Edge> from = network.edge(m.fromEdgeId());
                    Optional<Edge> to = network.edge(m.toEdgeId());
                    boolean valid = from.isPresent() && to.isPresent()
                            && from.get().toNodeId().equals(nodeId) && to.get().fromNodeId().equals(nodeId);
                    if (!valid) {
                        issues.add(error("SIGNAL_INVALID_MOVEMENT", "El movimiento " + m.fromEdgeId() + " → "
                                + m.toEdgeId() + " no pasa por la intersección '" + nodeId + "'.", nodeId));
                        movementsValid = false;
                    } else if (!rules.allows(from.get(), to.get())) {
                        issues.add(error("SIGNAL_MOVEMENT_NOT_ALLOWED", "El semáforo en '" + nodeId
                                + "' da verde al giro " + m.fromEdgeId() + " → " + m.toEdgeId()
                                + ", que está prohibido (o es un giro en U).", nodeId));
                    }
                }
                if (networkValid && movementsValid) {
                    checkPhaseConflicts(network, network.node(nodeId).orElseThrow(), phase, p + 1, issues);
                }
            }
        }
    }

    private static void checkPhaseConflicts(Network network, Node node, SignalPhase phase, int phaseNumber,
            List<ValidationIssue> issues) {
        List<Movement> green = phase.greenMovements();
        for (int i = 0; i < green.size(); i++) {
            for (int j = i + 1; j < green.size(); j++) {
                Movement a = green.get(i);
                Movement b = green.get(j);
                if (MovementConflicts.conflict(network, node, a, b)) {
                    issues.add(error("SIGNAL_CONFLICTING_MOVEMENTS", "En la fase " + phaseNumber
                            + " del semáforo en '" + node.id() + "', los movimientos " + a.fromEdgeId() + " → "
                            + a.toEdgeId() + " y " + b.fromEdgeId() + " → " + b.toEdgeId()
                            + " se cruzan y no pueden tener verde a la vez.", node.id()));
                }
            }
        }
    }

    private static boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private static ValidationIssue error(String code, String message, String elementId) {
        return new ValidationIssue(Severity.ERROR, code, message, elementId);
    }

    private static ValidationIssue warning(String code, String message, String elementId) {
        return new ValidationIssue(Severity.WARNING, code, message, elementId);
    }
}
