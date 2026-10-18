# syntax=docker/dockerfile:1
# Imagen del backend (Spring Boot + motor). Contexto de construcción: la carpeta backend/.

# --- Compilación -----------------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /src

# Primero solo los pom: las dependencias quedan en caché mientras no cambien.
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY engine/pom.xml engine/
COPY app/pom.xml app/
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline -DexcludeArtifactIds=simucity-engine || true

COPY engine/src engine/src
COPY app/src app/src
# Las pruebas se ejecutan en CI (necesitan Docker para Testcontainers); aquí solo se empaqueta.
RUN ./mvnw -q -B -DskipTests package

# --- Ejecución -------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S simucity && adduser -S -G simucity -H simucity
WORKDIR /app
COPY --from=build /src/app/target/simucity-app-*.jar /app/app.jar

# Nunca como root.
USER simucity
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"UP"' || exit 1

# MaxRAMPercentage: la JVM respeta el límite de memoria del contenedor.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
