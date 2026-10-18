---
name: migracion-db
description: Reglas para cambiar el esquema de PostgreSQL de SimuCity Lab con Flyway (backend/app/src/main/resources/db/migration) - nombres de archivo, migraciones inmutables, tipos, restricciones e índices, y su relación con las entidades JPA. Úsala cuando el usuario pida crear o modificar tablas, columnas, índices o entidades persistentes.
---

# Migraciones de base de datos — SimuCity Lab

El esquema lo controla **Flyway**. Hibernate solo valida (`ddl-auto: validate`): si la entidad y la tabla no coinciden, la aplicación no arranca.

## Reglas

1. **Nunca editar una migración ya creada** (aunque solo exista en local de otra persona, puede estar aplicada). Todo cambio es un archivo nuevo.
2. Nombre: `V{n}__descripcion_en_snake_case.sql`, con `n` = siguiente número entero. Revisar las existentes antes de elegirlo.
3. Una migración = un cambio coherente (p. ej. "tablas de escenarios"). Comentario breve al inicio explicando el propósito.
4. Tipos:
   - IDs: `UUID PRIMARY KEY` (generados por la aplicación con `UUID.randomUUID()`).
   - Fechas: `TIMESTAMPTZ NOT NULL`, en UTC.
   - Enums: `VARCHAR(n)` + `CHECK (col IN (...))`; en JPA `@Enumerated(EnumType.STRING)`. Ampliar un enum = nueva migración que reemplaza el CHECK.
   - Textos: `VARCHAR(n)` con el mismo límite que la validación del DTO.
   - Snapshots de configuración (red, demanda, semáforos, parámetros de una ejecución): `JSONB NOT NULL`, inmutables una vez creada la ejecución.
5. Integridad en la base de datos, no solo en Java: `NOT NULL`, `CHECK`, `UNIQUE`, claves foráneas con `ON DELETE` explícito.
6. Índice para cada consulta habitual, empezando por la columna de dueño o de padre (p. ej. `(project_id, created_at DESC)`).
7. Métricas: no guardar el estado de cada vehículo por tick (sección 15.1); solo métricas agregadas y muestras seleccionadas.

## Entidades JPA

- Cada columna nueva se refleja en la entidad en el mismo cambio.
- Referencias a otros módulos por ID (`UUID projectId`), no con `@ManyToOne` a entidades de otro paquete.
- `updatable = false` en columnas que no cambian (dueño, fecha de creación, snapshots).

## Verificación

- `./mvnw test` aplica todas las migraciones sobre un PostgreSQL limpio (Testcontainers) y Hibernate valida el esquema.
- Para la base local de desarrollo: arrancar la app con el perfil `dev`; Flyway aplica las pendientes.
- Documentar la migración en la tabla "Base de datos" de `docs/arquitectura-backend.md`.
