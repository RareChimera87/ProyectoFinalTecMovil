#!/usr/bin/env bash
# Función que usa desplegar.sh (se carga con `source`, no se ejecuta sola).
#
# #103 (C-M5) endureció el arranque: ENV=local exige ALLOW_FAKE_AUTH=true. Una imagen anterior a ese
# commit no lo exige, así que revertir a ella puede dejar la API arrancada en local sin la bandera. Tras
# el primer despliegue con #103 la imagen de reversión (PARCHEU_PREV_TAG) ya lo incluye y el aviso calla.

# Commit de #103 en dev ("Endurece ENV=local con ALLOW_FAKE_AUTH y cierra el puerto de la LAN").
COMMIT_103="76aa9c51c440502f0258bfb3ca8a5078130d1eb0"

# aviso_reversion_anterior_a_103 <repo> <etiqueta>
# Imprime un AVISO en stderr si la etiqueta (el SHA corto del commit con el que se construyó la imagen) es
# anterior a #103 o no se puede ubicar en el historial. No cambia el código de salida: solo informa.
aviso_reversion_anterior_a_103() {
  local repo="$1" etiqueta="${2:-}"
  [ -n "$etiqueta" ] || return 0
  case "$etiqueta" in
    *-dirty | homelab | dev | *[!0-9a-f]*)
      echo "AVISO: no se puede saber si la imagen de reversión $etiqueta incluye #103 (etiqueta sin SHA limpio); si es anterior, revertir a ella puede arrancar ENV=local sin ALLOW_FAKE_AUTH (README, «Reversión»)." >&2
      return 0 ;;
  esac
  if ! git -C "$repo" cat-file -e "$etiqueta^{commit}" 2>/dev/null; then
    echo "AVISO: la imagen de reversión $etiqueta no está en el historial de este repo; no se sabe si incluye #103 (README, «Reversión»)." >&2
    return 0
  fi
  # Ancestro estricto de #103 (el propio commit de #103 ya lo incluye).
  if git -C "$repo" merge-base --is-ancestor "$etiqueta" "$COMMIT_103^" 2>/dev/null; then
    echo "AVISO: la imagen de reversión $etiqueta es anterior a #103 ($COMMIT_103): si el despliegue nuevo falla y se revierte a ella, arrancará sin exigir ALLOW_FAKE_AUTH en ENV=local. Tras este despliegue la reversión ya será segura (README, «Reversión»)." >&2
  fi
  return 0
}
