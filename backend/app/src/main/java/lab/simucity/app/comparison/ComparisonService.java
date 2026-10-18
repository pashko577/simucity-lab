package lab.simucity.app.comparison;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lab.simucity.app.comparison.ComparisonDtos.ComparisonResponse;
import lab.simucity.app.comparison.ComparisonDtos.Condition;
import lab.simucity.app.comparison.ComparisonDtos.RunRef;
import lab.simucity.app.comparison.FairnessCheck.RunFacts;
import lab.simucity.app.runs.RunService;
import lab.simucity.app.runs.SimulationRunRecord;
import lab.simucity.app.scenarios.ScenarioCodec;
import lab.simucity.app.scenarios.ScenarioDefinition;
import lab.simucity.app.scenarios.ScenarioService;
import lab.simucity.app.shared.BadRequestException;
import lab.simucity.app.shared.NotFoundException;
import lab.simucity.engine.metrics.MetricScope;
import lab.simucity.engine.simulation.RunParameters;
import tools.jackson.databind.json.JsonMapper;

/**
 * Compara dos ejecuciones terminadas del usuario. Se calcula al vuelo a partir de los snapshots y métricas
 * guardados: no hay nada que persistir y el resultado siempre es coherente con las ejecuciones.
 */
@Service
public class ComparisonService {

    private final RunService runs;
    private final ScenarioService scenarios;
    private final ScenarioCodec codec;
    private final JsonMapper json;

    ComparisonService(RunService runs, ScenarioService scenarios, ScenarioCodec codec, JsonMapper json) {
        this.runs = runs;
        this.scenarios = scenarios;
        this.codec = codec;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public ComparisonResponse compare(UUID ownerId, UUID baseRunId, UUID candidateRunId) {
        if (baseRunId.equals(candidateRunId)) {
            throw new BadRequestException("Elige dos ejecuciones distintas para comparar.");
        }
        // getOwned → 404 si es ajena; metrics → 409 si aún no terminó.
        SimulationRunRecord base = runs.getOwned(ownerId, baseRunId);
        SimulationRunRecord candidate = runs.getOwned(ownerId, candidateRunId);

        RunFacts baseFacts = facts(base);
        RunFacts candidateFacts = facts(candidate);
        List<Condition> conditions = FairnessCheck.conditions(baseFacts, candidateFacts);

        return new ComparisonResponse(
                ref(ownerId, base, baseFacts),
                ref(ownerId, candidate, candidateFacts),
                FairnessCheck.isFair(conditions),
                conditions,
                ScenarioDiff.between(baseFacts.definition(), candidateFacts.definition()),
                MetricComparator.compare(runs.metrics(ownerId, baseRunId, MetricScope.NETWORK),
                        runs.metrics(ownerId, candidateRunId, MetricScope.NETWORK)),
                MetricComparator.compare(runs.metrics(ownerId, baseRunId, MetricScope.SEGMENT),
                        runs.metrics(ownerId, candidateRunId, MetricScope.SEGMENT)));
    }

    private RunFacts facts(SimulationRunRecord run) {
        RunParameters params = json.readValue(run.getParametersSnapshot(), RunParameters.class);
        ScenarioDefinition definition = codec.read(run.getScenarioSnapshot());
        return new RunFacts(run.getEngineVersion(), params, definition);
    }

    /** El nombre es el actual del escenario; si se borró, la ejecución tampoco existiría (borrado en cascada). */
    private RunRef ref(UUID ownerId, SimulationRunRecord run, RunFacts facts) {
        String name;
        try {
            name = scenarios.getOwned(ownerId, run.getScenarioId()).getName();
        } catch (NotFoundException deleted) {
            name = "(escenario eliminado)";
        }
        return new RunRef(run.getId(), run.getScenarioId(), name, run.getSeed(), run.getEngineVersion(),
                facts.parameters().durationSeconds());
    }
}
