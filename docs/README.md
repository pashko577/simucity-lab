# Documentación de SimuCity Lab

Documentación técnica de lo que está construido. La visión y los requisitos del producto están en
[SimuCity_Lab_Documentacion.md](../SimuCity_Lab_Documentacion.md).

| Documento | Contenido |
|---|---|
| [motor.md](motor.md) | Motor de simulación: modelo, ciclo por ticks, determinismo, rutas, semáforos, métricas |
| [arquitectura-backend.md](arquitectura-backend.md) | Aplicación Spring Boot: módulos, seguridad, base de datos, API |
| [frontend.md](frontend.md) | Editor web: pantallas, React Flow, animación, cliente de la API, pruebas |
| [limitaciones.md](limitaciones.md) | Limitaciones conocidas y decisiones pendientes |

## Estado por fase

| Fase | Estado |
|---|---|
| 1. Definición | Especificación disponible |
| 2. Motor independiente | ✅ Completa (motor v0.2.0, 54 pruebas) |
| 3. Editor web | ✅ Editor 2D con React Flow: dibujar la red, semáforos, giros, demanda, validar (43 pruebas + E2E) |
| 4. Ejecución y resultados | ✅ Ejecuciones asíncronas, métricas explicadas, CSV y animación 2D sobre el lienzo (35 pruebas de API) |
| 5. Comparación | Pendiente. Ya existen la duplicación de escenarios, los snapshots y las semillas |
| Vista 3D | Pendiente, después de la Fase 5 (sección 17, prioridad 4). La animación ya usa la interfaz `RunRenderer` |
| 6. Seguridad y despliegue | Parcial: autenticación, CSRF, autorización por recurso, límites de tamaño y de ejecución |

## Desarrollo local

Requisitos: JDK 21 o superior, Node 24 y Docker Desktop. Maven no hace falta: se usa el wrapper.

```bash
# 1. Base de datos (PostgreSQL 17 en el puerto 55432 de localhost)
docker compose -f docker/compose.yaml up -d

# 2. Backend: pruebas y API en http://localhost:8088
cd backend
./mvnw install                    # Windows: mvnw.cmd install (pruebas incluidas; requiere Docker)
java -jar app/target/simucity-app-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev

# 3. Frontend en http://localhost:5173 (otra terminal)
cd frontend
npm install
npm run dev
```

Abre http://localhost:5173, crea una cuenta y empieza un proyecto.

**Puertos de esta máquina:** PostgreSQL usa el 55432 (el 5432 lo ocupa un PostgreSQL instalado) y el backend en
modo dev usa el 8088 (el 8080 lo ocupa WSL). Se pueden cambiar con `SIMUCITY_DB_PORT`, `SIMUCITY_PORT` y,
en el frontend, `SIMUCITY_API_URL`.
