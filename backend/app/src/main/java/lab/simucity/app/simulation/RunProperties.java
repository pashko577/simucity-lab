package lab.simucity.app.simulation;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Límites de ejecución configurables ({@code simucity.runs.*} en application.yml). */
@ConfigurationProperties("simucity.runs")
public record RunProperties(
        int workerThreads,
        int queueCapacity,
        int maxActivePerUser,
        double maxDurationSeconds,
        Duration timeout,
        long maxExpectedVehicles,
        long maxSampleCells) {
}
