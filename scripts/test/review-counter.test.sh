#!/usr/bin/env bash
# Pruebas de scripts/review-counter.py con fixtures de docs/revisiones.md (E0.6).
set -euo pipefail
raiz="$(cd "$(dirname "$0")/../.." && pwd)"
fx="$raiz/scripts/test/fixtures/review-counter"
fallos=0

correr() { # <revisiones> <prs> <issues> [duenos]
  python3 "$raiz/scripts/review-counter.py" --revisiones "$fx/$1" --backlog "$raiz/docs/backlog.md" \
    --prs "$fx/$2" --issues "$fx/$3" --duenos "${4:-{\}}"
}

esperar() { # <nombre> <filtro jq que debe dar true> <salida>
  if jq -e "$2" >/dev/null <<<"$3"; then
    echo "ok   - $1"
  else
    echo "FALLA - $1: $2"; echo "$3"; fallos=$((fallos + 1))
  fi
}

salida="$(correr revisiones-inicial.md prs-dos.json issues-vacio.json)"
esperar "2 pequeñas no abren issue" '.accion == "nada" and .conteo == 2' "$salida"

salida="$(correr revisiones-inicial.md prs.json issues-vacio.json)"
esperar "3 pequeñas abren exactamente un issue (major y fix/* no cuentan)" \
  '.accion == "abrir" and .conteo == 3 and .prs == [2, 7, 6]' "$salida"
esperar "el issue va al dueño de la ola de la feature que completa el lote (E0.8: I3, ola 3)" \
  '.dueno == "I3" and .titulo == "Revisión de 3 features pequeñas (ola 3)" and .asignado == "RareChimera87"' "$salida"

salida="$(correr revisiones-inicial.md prs.json issues-vacio.json '{"I3":"usuario-i3"}')"
esperar "DUENOS_GITHUB asigna al usuario del dueño" '.asignado == "usuario-i3"' "$salida"

salida="$(correr revisiones-inicial.md prs.json issues-abierto.json)"
esperar "re-ejecutar tras abrir el issue no abre otro" '.accion == "nada" and .conteo == 0' "$salida"

salida="$(correr revisiones-inicial.md prs-despues.json issues-abierto.json)"
esperar "la feature siguiente empieza un lote nuevo" '.accion == "nada" and .conteo == 1 and .prs == [12]' "$salida"

salida="$(correr revisiones-con-revision.md prs-despues.json issues-vacio.json)"
esperar "la revisión anotada en revisiones.md reinicia el contador" \
  '.accion == "nada" and .conteo == 1 and (.frontera | startswith("2026-10-29T00:00:00-05:00"))' "$salida"

salida="$(correr no-existe.md prs.json issues-vacio.json)"
esperar "sin docs/revisiones.md cuenta desde el inicio" '.accion == "abrir" and .frontera == null' "$salida"

if (( fallos > 0 )); then echo "$fallos prueba(s) fallaron"; exit 1; fi
echo "review-counter: todas las pruebas pasaron"
