#!/usr/bin/env bash
# Copia de seguridad de PostgreSQL del stack de Docker (compose.yaml).
#
#   scripts/backup.sh                 # crea backups/simucity-AAAAMMDD-HHMMSS.dump
#   KEEP=30 scripts/backup.sh         # conserva las 30 copias más recientes (por defecto 14)
#
# Formato "custom" de pg_dump: comprimido y restaurable por partes con pg_restore.
# Programarlo a diario (cron en Linux, Programador de tareas en Windows) y copiar la carpeta backups/ fuera
# del servidor. Las copias contienen datos de usuarios: no subirlas a Git (backups/ está en .gitignore).
set -euo pipefail

cd "$(dirname "$0")/.."
KEEP="${KEEP:-14}"
mkdir -p backups
FILE="backups/simucity-$(date +%Y%m%d-%H%M%S).dump"

docker compose exec -T db pg_dump -U simucity -d simucity --format=custom --no-owner > "$FILE.partial"
mv "$FILE.partial" "$FILE"   # nunca dejar a medias un archivo con nombre definitivo
echo "Copia creada: $FILE ($(du -h "$FILE" | cut -f1))"

# Retención: borrar las más antiguas por encima de KEEP.
ls -1t backups/simucity-*.dump | tail -n +"$((KEEP + 1))" | while read -r old; do
    rm -f -- "$old"
    echo "Eliminada por retención: $old"
done
