package lab.simucity.engine.simulation;

import lab.simucity.engine.validation.ScenarioValidator;
import lab.simucity.engine.validation.ValidationResult;

/**
 * Punto de entrada del motor. Sin estado: se puede reutilizar y compartir entre hilos;
 * cada llamada a {@link #run} crea su propia ejecución aislada.
 *
 * <p>Modelo (MVP): colas por segmento. Cada vehículo recorre una calle en
 * {@code longitud / velocidad} y luego espera en una cola FIFO hasta que (1) el semáforo autoriza su
 * movimiento, (2) la calle tiene capacidad de descarga disponible (flujo de saturación por carril) y
 * (3) la calle siguiente tiene espacio de almacenamiento. Las rutas son de menor tiempo a flujo libre
 * y se calculan una vez al inicio.
 */
public final class SimulationEngine {

    private final ScenarioValidator validator;

    public SimulationEngine() {
        this(new ScenarioValidator());
    }

    public SimulationEngine(ScenarioValidator validator) {
        this.validator = validator;
    }

    /**
     * @throws InvalidScenarioException si la validación encuentra errores; contiene todos los problemas
     */
    public SimulationResult run(Scenario scenario, RunParameters params) {
        return run(scenario, params, RunControl.NONE);
    }

    /**
     * @throws InvalidScenarioException   si la validación encuentra errores
     * @throws SimulationAbortedException si {@code control} pide detener la ejecución
     */
    public SimulationResult run(Scenario scenario, RunParameters params, RunControl control) {
        ValidationResult validation = validator.validate(scenario, params);
        if (!validation.isValid()) {
            throw new InvalidScenarioException(validation);
        }
        return new SimulationRun(scenario, params, validation.warnings()).execute(control);
    }

    public ValidationResult validate(Scenario scenario, RunParameters params) {
        return validator.validate(scenario, params);
    }
}
