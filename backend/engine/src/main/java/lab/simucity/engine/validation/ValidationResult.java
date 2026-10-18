package lab.simucity.engine.validation;

import java.util.List;

public record ValidationResult(List<ValidationIssue> issues) {

    public ValidationResult {
        issues = List.copyOf(issues);
    }

    /** {@code true} si no hay errores (las advertencias no bloquean la ejecución). */
    public boolean isValid() {
        return errors().isEmpty();
    }

    public List<ValidationIssue> errors() {
        return issues.stream().filter(i -> i.severity() == Severity.ERROR).toList();
    }

    public List<ValidationIssue> warnings() {
        return issues.stream().filter(i -> i.severity() == Severity.WARNING).toList();
    }

    public boolean hasCode(String code) {
        return issues.stream().anyMatch(i -> i.code().equals(code));
    }
}
