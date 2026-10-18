package lab.simucity.app.comparison;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import lab.simucity.app.comparison.ComparisonDtos.ChangeKind;
import lab.simucity.app.comparison.ComparisonDtos.ElementType;
import lab.simucity.app.comparison.ComparisonDtos.FieldChange;
import lab.simucity.app.comparison.ComparisonDtos.ScenarioChange;
import lab.simucity.app.scenarios.ScenarioDefinition;
import lab.simucity.app.scenarios.ScenarioDefinition.DemandDef;
import lab.simucity.app.scenarios.ScenarioDefinition.EdgeDef;
import lab.simucity.app.scenarios.ScenarioDefinition.MovementDef;
import lab.simucity.app.scenarios.ScenarioDefinition.NodeDef;
import lab.simucity.app.scenarios.ScenarioDefinition.PeriodDef;
import lab.simucity.app.scenarios.ScenarioDefinition.SignalDef;

/**
 * Diferencias entre dos definiciones de escenario, elemento por elemento (por ID). Las posiciones de los
 * nodos no se comparan: son solo visuales y no afectan al resultado.
 */
final class ScenarioDiff {

    private ScenarioDiff() {
    }

    static List<ScenarioChange> between(ScenarioDefinition base, ScenarioDefinition candidate) {
        List<ScenarioChange> changes = new ArrayList<>();
        diff(changes, ElementType.NODE, base.nodes(), candidate.nodes(), NodeDef::id, ScenarioDiff::nodeFields);
        diff(changes, ElementType.EDGE, base.edges(), candidate.edges(), EdgeDef::id, ScenarioDiff::edgeFields);
        diff(changes, ElementType.DEMAND, base.demands(), candidate.demands(), DemandDef::id, ScenarioDiff::demandFields);
        diff(changes, ElementType.SIGNAL, base.signals(), candidate.signals(), SignalDef::nodeId, ScenarioDiff::signalFields);
        diff(changes, ElementType.TURN, base.prohibitedMovements(), candidate.prohibitedMovements(),
                m -> m.from() + "→" + m.to(), m -> Map.of());
        return changes;
    }

    /** ¿Tienen ambas definiciones exactamente la misma demanda? (condición de comparación justa). */
    static boolean sameDemand(ScenarioDefinition base, ScenarioDefinition candidate) {
        List<ScenarioChange> changes = new ArrayList<>();
        diff(changes, ElementType.DEMAND, base.demands(), candidate.demands(), DemandDef::id, ScenarioDiff::demandFields);
        return changes.isEmpty();
    }

    private static <T> void diff(List<ScenarioChange> out, ElementType type, List<T> base, List<T> candidate,
            Function<T, String> id, Function<T, Map<String, Object>> fields) {
        Map<String, T> before = index(base, id);
        Map<String, T> after = index(candidate, id);
        Set<String> ids = new TreeSet<>(Comparator.naturalOrder());
        ids.addAll(before.keySet());
        ids.addAll(after.keySet());

        for (String elementId : ids) {
            T b = before.get(elementId);
            T a = after.get(elementId);
            if (b == null) {
                out.add(new ScenarioChange(type, elementId, ChangeKind.ADDED, List.of()));
            } else if (a == null) {
                out.add(new ScenarioChange(type, elementId, ChangeKind.REMOVED, List.of()));
            } else {
                List<FieldChange> changed = fieldChanges(fields.apply(b), fields.apply(a));
                if (!changed.isEmpty()) {
                    out.add(new ScenarioChange(type, elementId, ChangeKind.MODIFIED, changed));
                }
            }
        }
    }

    private static <T> Map<String, T> index(List<T> items, Function<T, String> id) {
        Map<String, T> map = new LinkedHashMap<>();
        items.forEach(item -> map.putIfAbsent(id.apply(item), item));
        return map;
    }

    private static List<FieldChange> fieldChanges(Map<String, Object> before, Map<String, Object> after) {
        List<FieldChange> changes = new ArrayList<>();
        before.forEach((field, value) -> {
            Object other = after.get(field);
            if (!Objects.equals(value, other)) {
                changes.add(new FieldChange(field, value, other));
            }
        });
        return changes;
    }

    private static Map<String, Object> nodeFields(NodeDef n) {
        return ordered("type", n.type());
    }

    private static Map<String, Object> edgeFields(EdgeDef e) {
        return ordered("from", e.from(), "to", e.to(), "lengthMeters", e.lengthMeters(), "lanes", e.lanes(),
                "speedLimitMps", e.speedLimitMps(), "storageCapacity", e.storageCapacity(), "closed", e.closed());
    }

    private static Map<String, Object> demandFields(DemandDef d) {
        List<PeriodDef> profile = d.profile() == null ? List.of() : d.profile();
        return ordered("origin", d.origin(), "destination", d.destination(),
                "vehiclesPerMinute", d.vehiclesPerMinute(), "mode", d.mode(), "profile", profile);
    }

    /** Un plan se describe por la duración y los movimientos de cada fase, legibles en la interfaz. */
    private static Map<String, Object> signalFields(SignalDef s) {
        List<String> phases = s.phases().stream()
                .map(p -> p.durationSeconds() + " s: " + (p.greenMovements().isEmpty() ? "todo rojo"
                        : String.join(", ", p.greenMovements().stream().map(ScenarioDiff::movement).toList())))
                .toList();
        return ordered("offsetSeconds", s.offsetSeconds(), "phases", phases);
    }

    private static String movement(MovementDef m) {
        return m.from() + "→" + m.to();
    }

    private static Map<String, Object> ordered(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
