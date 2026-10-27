#!/usr/bin/env bash
# Respaldo diario de la base de ParcheU (corre en el homeserver, por cron). Rotación de 7 días.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DESTINO="$DIR/backups"
mkdir -p "$DESTINO"
cd "$DIR"

# Rota respaldo.log (el cron escribe ahí) cuando pasa de 1 MiB; se conserva una copia.
LOG="$DESTINO/respaldo.log"
if [ -f "$LOG" ] && [ "$(stat -c %s "$LOG")" -gt 1048576 ]; then mv -f "$LOG" "$LOG.1"; fi

ARCHIVO="$DESTINO/parcheu-$(date +%Y%m%d-%H%M%S).sql.gz"
TMP="$ARCHIVO.tmp"
trap 'rm -f "$TMP"' EXIT

docker compose exec -T parcheu-db pg_dump -U parcheu --no-owner parcheu | gzip -9 > "$TMP"
# Un dump vacío o truncado no debe reemplazar nada ni contar como respaldo.
if ! gzip -t "$TMP" || [ "$(stat -c %s "$TMP")" -lt 200 ]; then
  echo "Respaldo inválido, se descarta" >&2
  exit 1
fi
mv "$TMP" "$ARCHIVO"
chmod 600 "$ARCHIVO"
find "$DESTINO" -maxdepth 1 -name 'parcheu-*.sql.gz' -mtime +6 -delete
echo "Respaldo listo: $ARCHIVO"
