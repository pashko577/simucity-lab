package lab.simucity.app.simulation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lab.simucity.engine.simulation.RunParameters;
import lab.simucity.engine.simulation.SimulationEngine;
import lab.simucity.engine.validation.NetworkValidator;
import lab.simucity.engine.validation.ScenarioValidator;

/** Integra el motor (Java puro) en Spring con los límites del servidor. */
@Configuration
@EnableConfigurationProperties(RunProperties.class)
public class SimulationConfig {

    @Bean
    SimulationEngine simulationEngine(RunProperties props) {
        ScenarioValidator.Limits engineDefaults = ScenarioValidator.Limits.MVP_DEFAULT;
        ScenarioValidator.Limits serverLimits = new ScenarioValidator.Limits(
                engineDefaults.minDtSeconds(),
                engineDefaults.maxDtSeconds(),
                Math.min(props.maxDurationSeconds(), engineDefaults.maxDurationSeconds()),
                engineDefaults.maxDemandPerMinute(),
                Math.min(props.maxExpectedVehicles(), engineDefaults.maxExpectedVehicles()),
                Math.min(props.maxSampleCells(), engineDefaults.maxSampleCells()));
        return new SimulationEngine(new ScenarioValidator(new NetworkValidator(), serverLimits));
    }

    /**
     * Pool acotado para simulaciones: nunca se ejecutan en el hilo de la petición HTTP.
     * Si la cola se llena, el envío se rechaza y la ejecución se marca como fallida (QUEUE_FULL).
     */
    @Bean
    ThreadPoolTaskExecutor simulationExecutor(RunProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.workerThreads());
        executor.setMaxPoolSize(props.workerThreads());
        executor.setQueueCapacity(props.queueCapacity());
        executor.setThreadNamePrefix("simulation-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    /** Valores por defecto para los parámetros que el cliente no envía. */
    public static RunParameters parameters(long seed, double durationSeconds, Double dt, Double sampleInterval,
            Double saturationFlow, Double stallThreshold) {
        return new RunParameters(seed,
                dt != null ? dt : RunParameters.DEFAULT_DT_SECONDS,
                durationSeconds,
                sampleInterval != null ? sampleInterval : RunParameters.DEFAULT_SAMPLE_INTERVAL_SECONDS,
                saturationFlow != null ? saturationFlow : RunParameters.DEFAULT_SATURATION_FLOW_VPH,
                stallThreshold != null ? stallThreshold : RunParameters.DEFAULT_STALL_THRESHOLD_SECONDS);
    }
}
