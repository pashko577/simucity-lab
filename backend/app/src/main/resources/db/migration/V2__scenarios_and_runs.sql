-- Escenarios (red + demanda + semáforos en JSONB) y ejecuciones del motor con sus resultados.

CREATE TABLE scenarios (
    id             UUID         PRIMARY KEY,
    project_id     UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    name           VARCHAR(120) NOT NULL,
    schema_version INT          NOT NULL,
    definition     JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT scenarios_schema_version_positive CHECK (schema_version > 0)
);

CREATE INDEX scenarios_project_updated_idx ON scenarios (project_id, updated_at DESC);

-- Cada ejecución guarda una copia inmutable de todo lo necesario para repetirla (sección 7.3).
CREATE TABLE simulation_runs (
    id                      UUID             PRIMARY KEY,
    scenario_id             UUID             NOT NULL REFERENCES scenarios (id) ON DELETE CASCADE,
    owner_id                UUID             NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status                  VARCHAR(20)      NOT NULL,
    engine_version          VARCHAR(20)      NOT NULL,
    seed                    BIGINT           NOT NULL,
    time_step               DOUBLE PRECISION NOT NULL,
    duration                DOUBLE PRECISION NOT NULL,
    parameters_snapshot     JSONB            NOT NULL,
    scenario_schema_version INT              NOT NULL,
    scenario_snapshot       JSONB            NOT NULL,
    result_summary          JSONB,
    created_at              TIMESTAMPTZ      NOT NULL,
    started_at              TIMESTAMPTZ,
    finished_at             TIMESTAMPTZ,
    error_code              VARCHAR(40),
    error_detail            VARCHAR(500),
    CONSTRAINT simulation_runs_status_valid
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX simulation_runs_scenario_created_idx ON simulation_runs (scenario_id, created_at DESC);
-- Límite de ejecuciones simultáneas por usuario y recuperación tras un reinicio.
CREATE INDEX simulation_runs_active_idx ON simulation_runs (owner_id) WHERE status IN ('PENDING', 'RUNNING');

CREATE TABLE simulation_metrics (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id       UUID             NOT NULL REFERENCES simulation_runs (id) ON DELETE CASCADE,
    position     INT              NOT NULL,
    metric_name  VARCHAR(60)      NOT NULL,
    metric_value DOUBLE PRECISION,
    unit         VARCHAR(20)      NOT NULL,
    scope_type   VARCHAR(20)      NOT NULL,
    scope_id     VARCHAR(64),
    observations BIGINT           NOT NULL,
    CONSTRAINT simulation_metrics_scope_valid CHECK (scope_type IN ('NETWORK', 'SEGMENT', 'ENTRY')),
    CONSTRAINT simulation_metrics_observations_non_negative CHECK (observations >= 0)
);

CREATE INDEX simulation_metrics_run_idx ON simulation_metrics (run_id, position);

-- Muestras para la animación en formato compacto (un arreglo por muestra, no una fila por calle y tick).
CREATE TABLE simulation_samples (
    run_id UUID  PRIMARY KEY REFERENCES simulation_runs (id) ON DELETE CASCADE,
    data   JSONB NOT NULL
);
