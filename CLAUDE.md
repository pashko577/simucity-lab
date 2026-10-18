# SimuCity Lab

Simulador urbano web educativo: el usuario dibuja una red vial, configura semáforos y demanda, ejecuta una simulación reproducible y compara escenarios con métricas.

La especificación completa está en [SimuCity_Lab_Documentacion.md](SimuCity_Lab_Documentacion.md). Ante cualquier duda de alcance o comportamiento, ese documento manda.

## Principios innegociables

1. **El motor de simulación es la fuente de verdad.** La animación solo reproduce estados ya calculados; el resultado nunca depende del renderizado ni del reloj real.
2. **Reproducibilidad.** Misma configuración + misma semilla + misma versión del motor = mismo resultado lógico. Cada ejecución guarda un snapshot inmutable de su configuración.
3. **Motor aislado.** El motor no depende de Spring, de la base de datos, de React ni del DOM. Se prueba con JUnit puro.
4. **Seguridad por recurso.** Todo acceso a proyecto, escenario, ejecución o exportación se autoriza en el backend contra el usuario autenticado. Nunca confiar en IDs enviados por el cliente (IDOR/BOLA). Denegar por defecto.
5. **Alcance MVP.** Modelo macroscópico de flujo y colas, editor 2D. Nada de 3D, microservicios, Kafka ni Kubernetes hasta que haya una necesidad medida.
6. **Honestidad del modelo.** Los resultados son exploratorios; los supuestos y limitaciones deben ser visibles en la interfaz.

## Stack

| Capa | Tecnología |
|---|---|
| Frontend | React + TypeScript + Vite |
| Editor de grafo | React Flow o SVG/Canvas |
| Animación | Canvas 2D o PixiJS |
| Gráficos | Recharts |
| Backend | Java 21 + Spring Boot 4.1.x (Spring Framework 7, Jakarta EE 11, Jackson 3), API REST `/api/v1` |
| Base de datos | PostgreSQL + Flyway |
| Pruebas | JUnit 5, Vitest + Testing Library, Playwright |
| Entorno | Docker / docker compose |

## Estructura prevista (monolito modular)

```
backend/
  engine/      # Módulo Java puro: grafo, motor, rutas, métricas. Sin Spring.
  app/         # Spring Boot: API, auth, persistencia, gestor de ejecuciones
frontend/      # React + Vite
docker/        # docker compose, configuración local
```

Módulos de dominio del backend: `Identity`, `Projects`, `Network`, `Scenarios`, `SimulationEngine`, `Metrics`, `Comparison`, `Exports`, `Audit`.

## Comandos

Maven no está instalado globalmente: usar siempre el wrapper desde `backend/`.

```bash
docker compose -f docker/compose.yaml up -d   # PostgreSQL local en el puerto 55432 (el 5432 lo usa un PostgreSQL instalado)
cd backend
./mvnw test                 # todas las pruebas (Windows: mvnw.cmd test). Las de app requieren Docker en ejecución.
./mvnw -pl engine test      # solo el motor (no requiere Docker)
./mvnw -DskipTests install && java -jar app/target/simucity-app-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev  # API en :8088 (el 8080 lo usa WSL)

cd frontend
npm run dev                 # http://localhost:5173, proxy /api → :8088
npm test && npm run typecheck
npm run e2e                 # Playwright: navegador real contra backend + Vite (requiere Docker y el jar construido)
```

El código se compila con `--release 21` (Java 21 LTS), aunque el JDK local sea más nuevo. No usar APIs posteriores a Java 21.

## Convenciones

- Código (identificadores, clases, funciones) en inglés; documentación, mensajes de interfaz y commits en español.
- Unidades internas del motor en SI: metros, segundos, m/s. Convertir solo en la interfaz y mostrar siempre la unidad.
- Las simulaciones se ejecutan como tareas asíncronas (`PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`), nunca dentro de una petición HTTP.
- Validar tamaño, rangos y números finitos en toda entrada; aplicar límites de nodos, aristas, duración y vehículos.
- Nunca registrar contraseñas, tokens ni secretos en logs; nunca devolver stack traces al cliente.

## Plan por fases

1. Definición (modelo, redes de prueba, métricas, prototipos)
2. **Motor independiente** con pruebas unitarias ← empezar aquí
3. Editor web 2D
4. Ejecución, API y resultados
5. Comparación reproducible
6. Seguridad y despliegue

## Documentación (obligatorio)

Todo lo que se construye se documenta en `docs/` (Markdown, en español), en la misma sesión:
- `docs/README.md`: índice, estado por fase y cómo ejecutar.
- `docs/motor.md`, `docs/arquitectura-backend.md` y los que se creen por cada parte nueva (p. ej. frontend).
- `docs/limitaciones.md`: toda limitación conocida o decisión pospuesta. Al resolverla, se marca como resuelta; no se borra.

## Skills del proyecto

- `motor-simulacion`: reglas para escribir o modificar cualquier código del motor (`backend/engine`).
- `endpoint-api`: crear o modificar endpoints REST en `backend/app` (autorización, validación, errores, pruebas).
- `migracion-db`: cambios de esquema con Flyway.
- `componente-frontend`: pantallas, editor (React Flow), animación y cliente de la API en `frontend/`.
