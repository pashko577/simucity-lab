#!/usr/bin/env bash
# Restauración de una copia creada con scripts/backup.sh.
#
#   scripts/restore.sh --check backups/simucity-X.dump   # PRUEBA: restaura en una base temporal, cuenta filas
#                                                        # de las tablas principales y la borra. No toca nada.
#   scripts/restore.sh backups/simucity-X.dump           # RESTAURA sobre la base real (pide confirmación)
#
# Probar la restauración periódicamente (sección 14.7): una copia que nunca se restauró no es una copia.
set -euo pipefail

cd "$(dirname "$0")/.."
MODE="restore"
if [[ "${1:-}" == "--check" ]]; then
    MODE="check"
    shift
fi
FILE="${1:?Uso: scripts/restore.sh [--check] backups/archivo.dump}"
[[ -f "$FILE" ]] || { echo "No existe: $FILE" >&2; exit 1; }

psql_db() { docker compose exec -T db psql -U simucity -v ON_ERROR_STOP=1 -d "$1" -tAc "$2"; }

if [[ "$MODE" == "check" ]]; then
    TMP="restore_check_$(date +%s)"
    psql_db postgres "CREATE DATABASE $TMP"
    trap 'psql_db postgres "DROP DATABASE IF EXISTS $TMP" >/dev/null' EXIT
    docker compose exec -T db pg_restore -U simucity -d "$TMP" --no-owner --exit-on-error < "$FILE"
    echo "Restauración de prueba correcta en la base temporal $TMP:"
    for table in flyway_schema_history users projects scenarios simulation_runs simulation_metrics run_batches audit_events; do
        printf '  %-22s %s filas\n' "$table" "$(psql_db "$TMP" "SELECT count(*) FROM $table")"
    done
    echo "Última migración: v$(psql_db "$TMP" "SELECT max(version::int) FROM flyway_schema_history WHERE success")"
    exit 0
fi

echo "ATENCIÓN: se reemplazarán TODOS los datos actuales por los de $FILE."
read -r -p "Escribe RESTAURAR para continuar: " answer
[[ "$answer" == "RESTAURAR" ]] || { echo "Cancelado."; exit 1; }

docker compose stop backend   # que nadie escriba durante la restauración
docker compose exec -T db pg_restore -U simucity -d simucity --clean --if-exists --no-owner --exit-on-error < "$FILE"
docker compose start backend
echo "Restaurado desde $FILE."
