-- Lotes: el mismo escenario ejecutado con N semillas consecutivas (sección 9.2: varias semillas, promedio y
-- dispersión). Cada ejecución del lote es una fila normal de simulation_runs con batch_id.

CREATE TABLE run_batches (
    id          UUID             PRIMARY KEY,
    scenario_id UUID             NOT NULL REFERENCES scenarios (id) ON DELETE CASCADE,
    owner_id    UUID             NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    size        INT              NOT NULL,
    base_seed   BIGINT           NOT NULL,
    duration    DOUBLE PRECISION NOT NULL,
    created_at  TIMESTAMPTZ      NOT NULL,
    CONSTRAINT run_batches_size_valid CHECK (size BETWEEN 2 AND 10)
);

CREATE INDEX run_batches_scenario_created_idx ON run_batches (scenario_id, created_at DESC);
CREATE INDEX run_batches_owner_idx ON run_batches (owner_id);

ALTER TABLE simulation_runs
    ADD COLUMN batch_id UUID REFERENCES run_batches (id) ON DELETE CASCADE;

CREATE INDEX simulation_runs_batch_idx ON simulation_runs (batch_id) WHERE batch_id IS NOT NULL;
