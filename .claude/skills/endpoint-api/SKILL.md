---
name: endpoint-api
description: Convenciones para crear o modificar endpoints REST, servicios, DTOs o seguridad en la aplicación Spring Boot de SimuCity Lab (backend/app) - autorización por recurso anti-IDOR, validación, errores Problem Details, paginación y pruebas de integración con Testcontainers. Úsala cuando el usuario pida un endpoint, un módulo nuevo del backend (escenarios, ejecuciones, métricas, exportación) o cambios de seguridad.
---

# Endpoints de la API — SimuCity Lab

Stack: Spring Boot 4.1.x, Spring Security 7, Hibernate 7, Jackson 3 (paquete `tools.jackson`), JUnit 6.
Los tutoriales de Boot 3 no siempre aplican: verificar APIs antes de usarlas.

## Estructura de un módulo

Paquete por módulo de dominio en `lab.simucity.app.<modulo>`, siguiendo `projects` como referencia:

```
<modulo>/
  Entidad.java            @Entity; constructor de paquete; mutadores con intención (rename, describe), sin setters
  EntidadRepository.java  interfaz de paquete; SOLO consultas filtradas por dueño o por un padre ya autorizado
  EntidadDtos.java        records de request (con Bean Validation) y response (con from(entidad))
  EntidadService.java     @Transactional; recibe UUID del usuario en cada método
  EntidadController.java  @RestController de paquete en /api/v1/...
```

## Autorización (obligatorio)

- El ID del usuario sale de `@AuthenticationPrincipal AppUserPrincipal user` → `user.id()`. Nunca del cuerpo, la URL ni una cabecera.
- Recurso ajeno = **404** (`NotFoundException`), igual que inexistente. Nunca 403 para recursos de otro usuario.
- Recursos hijos de un proyecto (escenarios, ejecuciones): autorizar primero con `ProjectService.getOwned(userId, projectId)` y luego buscar el hijo **filtrando por ese proyecto**.
- Las tareas asíncronas y las descargas también verifican el dueño (sección 14.2).
- Denegar por defecto: una ruta pública nueva se declara explícitamente en `SecurityConfig` y debe justificarse.

## Validación y errores

- `@Valid` en todo `@RequestBody`; límites de tamaño en todos los strings y colecciones.
- Excepciones de `lab.simucity.app.shared`: `NotFoundException` (404), `ConflictException` (409), `BadRequestException` (400). `ApiExceptionHandler` las convierte a Problem Details en español.
- Nunca devolver mensajes de excepciones internas, trazas ni nombres de clases.
- Listados: siempre paginados con `PageRequests.of(...)` y respondidos con `PageResponse`; orden estable (incluir `id` como desempate).
- Fechas con el `Clock` inyectado (`clock.instant()`), nunca `Instant.now()`.

## Ejecuciones de simulación (cuando se implementen)

- Nunca ejecutar el motor dentro de la petición HTTP: crear el registro en `PENDING` y procesarlo de forma asíncrona.
- Guardar un snapshot inmutable del escenario, la semilla y `EngineVersion.CURRENT` con cada ejecución.
- Los errores de validación del motor (`InvalidScenarioException`) se devuelven como 400 con la lista de `ValidationIssue`.

## Pruebas (obligatorio)

Cada endpoint nuevo lleva pruebas en `app/src/test` que extienden `ApiTestSupport` (aplicación completa + PostgreSQL real):

- Camino feliz.
- **Otro usuario recibe 404** en GET/PATCH/PUT/DELETE del recurso (y no lo ve en listados).
- 401 sin sesión.
- 400 con datos inválidos.
- Peticiones mutantes con `.with(csrf())`.

En MockMvc el repositorio CSRF se sustituye por uno de prueba: no afirmar el nombre de la cabecera ni la cookie ahí.

## Al terminar

- [ ] `./mvnw test` en verde (requiere Docker).
- [ ] Tabla de API y estado de módulos actualizados en `docs/arquitectura-backend.md`.
- [ ] Limitaciones nuevas en `docs/limitaciones.md`.
