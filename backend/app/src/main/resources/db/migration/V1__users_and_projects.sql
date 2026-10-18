-- Usuarios y proyectos. Las tablas de escenarios y ejecuciones llegan en migraciones posteriores.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT users_status_valid    CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX users_email_uq ON users (email);

CREATE TABLE projects (
    id          UUID          PRIMARY KEY,
    owner_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(120)  NOT NULL,
    description VARCHAR(2000),
    visibility  VARCHAR(20)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT projects_visibility_valid CHECK (visibility IN ('PRIVATE'))
);

-- Listado de "mis proyectos" ordenado por actividad reciente.
CREATE INDEX projects_owner_updated_idx ON projects (owner_id, updated_at DESC);
