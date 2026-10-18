package lab.simucity.app.runs;

import java.util.List;
import java.util.Locale;

import lab.simucity.app.runs.RunDtos.MetricDto;

/**
 * Exporta métricas a CSV (RFC 4180). Cada fila lleva la ejecución, la versión del motor y la semilla para que
 * el archivo sea autoexplicativo al abrirlo fuera de la aplicación.
 *
 * <p>Protección contra inyección de fórmulas: los textos que empiezan por {@code = + - @}, tabulación o
 * retorno de carro (p. ej. un ID de calle escrito por el usuario) se prefijan con {@code '} para que Excel o
 * LibreOffice no los ejecuten como fórmula.
 */
final class RunCsvExporter {

    private RunCsvExporter() {
    }

    static String export(SimulationRunRecord run, List<MetricDto> metrics) {
        StringBuilder csv = new StringBuilder(64 * (metrics.size() + 1));
        csv.append("run_id,engine_version,seed,metric_name,metric_value,unit,scope_type,scope_id,observations\r\n");
        for (MetricDto m : metrics) {
            csv.append(run.getId()).append(',')
                    .append(text(run.getEngineVersion())).append(',')
                    .append(run.getSeed()).append(',')
                    .append(text(m.name())).append(',')
                    .append(m.value() == null ? "" : number(m.value())).append(',')
                    .append(text(m.unit())).append(',')
                    .append(m.scope().name()).append(',')
                    .append(m.scopeId() == null ? "" : text(m.scopeId())).append(',')
                    .append(m.observations())
                    .append("\r\n");
        }
        return csv.toString();
    }

    /** Punto como separador decimal siempre, sin depender del idioma del servidor. */
    private static String number(double value) {
        return String.format(Locale.ROOT, "%s", value);
    }

    static String text(String value) {
        String safe = value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
