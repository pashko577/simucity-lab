package lab.simucity.app.runs;

import java.util.Set;

public enum RunStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public static final Set<RunStatus> ACTIVE = Set.of(PENDING, RUNNING);

    public boolean isTerminal() {
        return !ACTIVE.contains(this);
    }
}
