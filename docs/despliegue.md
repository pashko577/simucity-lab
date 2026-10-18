# Despliegue, operación y seguridad (Fase 6)

## Levantar la aplicación completa con Docker

```bash
cp .env.example .env               # y define DB_PASSWORD (p. ej. openssl rand -base64 32)
docker compose up -d --build       # db + backend + frontend
# → http://localhost:8081
docker compose ps                  # los tres deben aparecer "healthy"
docker compose logs -f backend     # registros
docker compose down                # detener (los datos quedan en el volumen pgdata)
```

```
                  navegador
                      │  :8081 (solo 127.0.0.1 por defecto)
          ┌───────────▼────────────┐
          │ frontend (nginx)       │  archivos estáticos + CSP y cabeceras de seguridad
          │   /api/* ──────────────┼──► backend (Spring Boot)  ──►  db (PostgreSQL 17)
          └────────────────────────┘        red interna de Docker, sin puertos publicados
```

| Servicio | Imagen | Medidas |
|---|---|---|
| `db` | `postgres:17-alpine` | Sin puerto publicado; volumen `pgdata`; healthcheck |
| `backend` | Construida desde `backend/Dockerfile` (JRE 21 Alpine) | Usuario sin privilegios, sistema de archivos de solo lectura, `cap_drop: ALL`, `no-new-privileges`, límite de 1 GB de memoria, healthcheck |
| `frontend` | Construida desde `frontend/Dockerfile` (`nginx-unprivileged`) | Igual que el backend; único servicio con puerto publicado |

Las imágenes compilan desde el código fuente (en varias etapas): no hace falta tener Java ni Node en el servidor.

### Variables (`.env`, nunca en Git)

| Variable | Para qué |
|---|---|
| `DB_PASSWORD` | Contraseña de PostgreSQL. Obligatoria: `docker compose` se niega a arrancar sin ella |
| `SECURE_COOKIES` | `true` (por defecto). Las cookies de sesión solo viajan por HTTPS (los navegadores lo permiten en `localhost`) |
| `HTTP_BIND` / `HTTP_PORT` | Dónde publica nginx (por defecto `127.0.0.1:8081`) |

### HTTPS (necesario fuera de localhost)

El stack no incluye certificados: depende del dominio y del servidor. Lo recomendado es poner delante un proxy
con HTTPS automático, por ejemplo **Caddy**, que obtiene y renueva certificados de Let's Encrypt:

```
# /etc/caddy/Caddyfile en el servidor
simucity.ejemplo.com {
    reverse_proxy 127.0.0.1:8081
    header Strict-Transport-Security "max-age=31536000; includeSubDomains"
}
```

Con eso: `SECURE_COOKIES=true` y `HTTP_BIND=127.0.0.1` (solo Caddy llega a nginx).

## Copias de seguridad (sección 14.7)

```bash
scripts/backup.sh                                   # backups/simucity-AAAAMMDD-HHMMSS.dump (conserva 14)
KEEP=30 scripts/backup.sh                           # conservar 30
scripts/restore.sh --check backups/simucity-X.dump  # PRUEBA en una base temporal: no toca nada
scripts/restore.sh backups/simucity-X.dump          # restauración real (pide escribir RESTAURAR)
```

- Formato `custom` de `pg_dump` (comprimido). El archivo se escribe como `.partial` y se renombra al terminar,
  así nunca queda una copia a medias con nombre definitivo.
- **Probado:** una copia de 60 KB se restauró en una base temporal con todas las tablas y la migración v4, y la
  base temporal se eliminó al terminar.
- **Pendiente para producción:** programarlo a diario (cron o Programador de tareas), copiar `backups/` fuera
  del servidor y ejecutar `--check` periódicamente. Las copias contienen datos de usuarios: `backups/` está en
  `.gitignore`.

## Seguridad: qué está implementado (sección 14)

| Requisito | Implementación | Verificado por |
|---|---|---|
| Contraseñas | BCrypt, de 12 a 128 caracteres, máximo 72 bytes | `AuthApiTest` |
| **Fuerza bruta (B2)** | 5 fallos en 15 min bloquean la cuenta; 20 fallos bloquean la IP; 10 registros por hora por IP. 429 con `Retry-After`. Se bloquea igual exista o no la cuenta | `LoginThrottleTest`, `LoginThrottleApiTest` |
| Sesión | Cookie `SESSION` HttpOnly, Secure, SameSite=Lax; 30 min; el ID cambia al iniciar sesión | `RealHttpSecurityTest` |
| **Sesiones persistentes (B4)** | Spring Session JDBC: sobreviven a reinicios y permiten varias instancias | `RealHttpSecurityTest` (fila en `SPRING_SESSION`, se borra al cerrar sesión) |
| CSRF | Patrón SPA; **el token se renueva al iniciar sesión (B3)** | `RealHttpSecurityTest` |
| Autorización (IDOR/BOLA) | Todo filtrado por dueño; recurso ajeno = 404 | Pruebas IDOR en todos los módulos |
| Validación y límites | Bean Validation, 2 MB por petición, límites del motor, 2 tareas por usuario, 120 s por ejecución | Pruebas de API |
| XSS | React escapa todo; **CSP estricta** (`script-src 'self'`, sin inline) | E2E con vigilante de CSP; prueba de control con script inyectado |
| Clickjacking | `X-Frame-Options: DENY` y `frame-ancestors 'none'` | `RealHttpSecurityTest`, `curl` |
| Errores | Problem Details sin trazas; `/error` público para no convertir 403 en 401 | `RealHttpSecurityTest` |
| Secretos | Variables de entorno; `.env` fuera de Git; `docker compose` exige `DB_PASSWORD` | — |
| **Auditoría (14.7)** | Tabla `audit_events`: registro, logins (éxito, fallo, bloqueo), logout, borrados. Sin email ni contraseña; retención de 180 días con purga diaria | `LoginThrottleApiTest` |
| Dependencias | `npm audit` (0 vulnerabilidades), plugins Maven actualizados, **Dependabot** semanal | CI |
| Contenedores | Sin root, solo lectura, sin capacidades, red interna | `docker compose exec … id` |

### Consultar la auditoría

```bash
docker compose exec db psql -U simucity -d simucity -c \
  "SELECT occurred_at, event_type, user_id, client_ip FROM audit_events ORDER BY id DESC LIMIT 20"
```

La IP real llega a través de nginx (`X-Forwarded-For`). Tomcat solo la acepta de proxies internos
(`server.forward-headers-strategy: native`), así que un cliente no puede falsearla.

## Rendimiento (sección 15)

Peor caso que admite el servidor: cuadrícula de 20×20 intersecciones, **todas con semáforo**, 1560 calles,
20 orígenes con 20 veh/min cada uno (~48 000 vehículos) y **2 horas simuladas** (14 400 ticks).

| Medición | Resultado | Límite |
|---|---|---|
| Solo el motor (`PerformanceTest`) | **2,0 s** | 120 s (`simucity.runs.timeout`) |
| De punta a punta por la API (cola + motor + guardar 6268 métricas y muestras en PostgreSQL) | **1,9 s** | — |
| Guardar el escenario (557 KB de JSON) | 0,24–0,42 s | 2 MB por petición |
| Leer las 6268 métricas | 49 ms | — |
| Leer las muestras de la animación (1,1 MB) | 52 ms | — |

```bash
./mvnw -pl engine test -Dperf=true -Dtest=PerformanceTest   # desde backend/
```

**Hallazgos de la medición:**
- Con una red grande y 2 horas, el muestreo cada 5 s superaba el límite de datos de animación (300 000 celdas)
  y la ejecución fallaba con `SAMPLES_TOO_MANY`. El editor no tiene campo para cambiarlo, así que el usuario
  quedaba bloqueado. **Corregido:** si el cliente no indica el intervalo, el servidor elige el más fino que
  cabe (5, 10, 15, 20, 30, 60 s…). En este caso, 60 s.
- En esa cuadrícula solo terminan ~2800 de 48 000 viajes: con un plan de una fase por acceso, cada calle tiene
  verde ~23 % del ciclo y la red se satura. Es un resultado correcto del modelo, e ilustra por qué hacen falta
  planes de dos fases (limitación F1).

## Integración continua (`.github/workflows/ci.yml`)

En cada `push` a `main` y en cada pull request:

| Trabajo | Qué hace |
|---|---|
| Backend | `./mvnw install`: motor + API con PostgreSQL real (Testcontainers) |
| Frontend | `npm ci`, chequeo de tipos, Vitest, `npm audit --audit-level=high` |
| E2E | Construye y levanta el stack de Docker y ejecuta Playwright contra nginx con la CSP real |

Permisos mínimos (`contents: read`). Dependabot (`.github/dependabot.yml`) propone actualizaciones semanales de
Maven, npm, imágenes de Docker y GitHub Actions; cada una pasa por el CI.

## Despliegue piloto: qué falta

El stack está listo para un servidor, pero desplegarlo requiere decisiones de infraestructura:
1. Un servidor (por ejemplo, un VPS con 2 GB de RAM y Docker) y un dominio.
2. HTTPS con Caddy (ver arriba) y un firewall que solo abra los puertos 80 y 443.
3. `scripts/backup.sh` programado y una copia externa.
4. Monitorizar `docker compose ps` y `/actuator/health` (un servicio externo como UptimeRobot sirve).
